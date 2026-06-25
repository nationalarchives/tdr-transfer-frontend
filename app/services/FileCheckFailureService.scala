package services

import configuration.ApplicationConfig
import io.circe.generic.auto._
import uk.gov.nationalarchives.aws.utils.s3.S3Clients._
import uk.gov.nationalarchives.aws.utils.s3.S3Utils
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusActions
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusActions.{StatusAction, StatusActionType}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusTypes.toStatusType
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues.StatusValue

import java.nio.file.Paths
import java.util.{Properties, UUID}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future, blocking}
import scala.util.Try

case class FileFormatMatch(
    puid: String,
    formatName: String
)

case class FileCheckStatus(
    id: String,
    statusType: String,
    statusName: String,
    statusValue: String
)

case class FileCheckStatusAction(
    statusName: String,
    actionType: String,
    message: String
)

case class FileCheckFailure(
    originalPath: String,
    filename: String,
    matches: List[FileFormatMatch],
    statusActions: List[FileCheckStatusAction]
)

private case class FileFormatResult(
    fileId: String,
    matches: List[FileFormatMatch]
)

private case class FileCheckResults(
    fileFormat: List[FileFormatResult]
)

private case class FileInfo(
    originalPath: String,
    fileCheckResults: FileCheckResults
)

private case class FileCheckError(
    file: FileInfo,
    statuses: List[FileCheckStatus]
)

object FileCheckFailureService {
  private val failureMessages: Properties = {
    val props = new Properties()
    val stream = getClass.getClassLoader.getResourceAsStream("validation-messages/file-check-failure-messages.properties")
    if (stream != null)
      try props.load(stream)
      finally stream.close()
    props
  }

  private def resolveMessage(status: FileCheckStatus, statusAction: StatusAction): FileCheckStatusAction = {
    val statusNameLabel = Option(failureMessages.getProperty(status.statusName)).getOrElse(status.statusName)
    val actionTypeLabel = Option(failureMessages.getProperty(statusAction.actionType.toString)).getOrElse(statusAction.actionType.toString)
    FileCheckStatusAction(
      statusName = statusNameLabel,
      actionType = actionTypeLabel,
      message = Option(failureMessages.getProperty(statusAction.messageKey)).getOrElse(statusAction.messageKey)
    )
  }

  // Maps the raw statuses of a file check error to the typed StatusActions (each carrying a StatusActionType).
  private def toStatusActions(fileCheckError: FileCheckError): List[(FileCheckStatus, StatusAction)] =
    fileCheckError.statuses.flatMap { status =>
      Try(toStatusType(status.statusName)).toOption.flatMap { statusType =>
        StatusActions.action(statusType, StatusValue(status.statusValue)).map(status -> _)
      }
    }
}

class FileCheckFailureService @Inject() (val applicationConfig: ApplicationConfig)(implicit val ec: ExecutionContext) {

  private val maxParallelS3Fetches = 10

  def getFileCheckFailures(consignmentId: UUID): Future[List[FileCheckFailure]] =
    getFileCheckFailures(consignmentId, S3Utils(s3Async(applicationConfig.s3Endpoint)))

  def getFileCheckFailures(consignmentId: UUID, s3Utils: S3Utils): Future[List[FileCheckFailure]] = {
    val bucket = applicationConfig.transferErrorsS3BucketName
    val prefix = s"$consignmentId/filechecks/"
    val objectsFuture = Future(blocking(s3Utils.listAllObjectsWithPrefix(bucket, prefix)))

    objectsFuture.flatMap { objects =>
      objects.grouped(maxParallelS3Fetches).foldLeft(Future.successful(List.empty[FileCheckFailure])) { (accFuture, batch) =>
        accFuture.flatMap { acc =>
          Future
            .traverse(batch) { s3Object =>
              Future {
                blocking {
                  val fileCheckError = s3Utils.decodeS3JsonObject[FileCheckError](bucket, s3Object.key())
                  val matches = fileCheckError.file.fileCheckResults.fileFormat.flatMap(_.matches)
                  val statusActions = FileCheckFailureService.toStatusActions(fileCheckError).map { case (status, action) =>
                    FileCheckFailureService.resolveMessage(status, action)
                  }
                  FileCheckFailure(
                    originalPath = fileCheckError.file.originalPath,
                    filename = Paths.get(fileCheckError.file.originalPath).getFileName.toString,
                    matches = matches,
                    statusActions = statusActions
                  )
                }
              }
            }
            .map(acc ++ _)
        }
      }
    }
  }

  def getFileCheckFailureStatusActionTypes(consignmentId: UUID): Future[Set[StatusActionType]] =
    getFileCheckFailureStatusActionTypes(consignmentId, S3Utils(s3Async(applicationConfig.s3Endpoint)))

  /** Returns the distinct StatusActionTypes (e.g. UserFixable or TNASupport) for a consignment's file check failures. These can be used to determine which
    * failure page to display.
    */
  def getFileCheckFailureStatusActionTypes(consignmentId: UUID, s3Utils: S3Utils): Future[Set[StatusActionType]] = {
    val bucket = applicationConfig.transferErrorsS3BucketName
    val prefix = s"$consignmentId/filechecks/"
    val objectsFuture = Future(blocking(s3Utils.listAllObjectsWithPrefix(bucket, prefix)))

    objectsFuture.flatMap { objects =>
      objects.grouped(maxParallelS3Fetches).foldLeft(Future.successful(Set.empty[StatusActionType])) { (accFuture, batch) =>
        accFuture.flatMap { acc =>
          Future
            .traverse(batch) { s3Object =>
              Future {
                blocking {
                  val fileCheckError = s3Utils.decodeS3JsonObject[FileCheckError](bucket, s3Object.key())
                  FileCheckFailureService.toStatusActions(fileCheckError).map { case (_, action) => action.actionType }
                }
              }
            }
            .map(acc ++ _.flatten)
        }
      }
    }
  }
}
