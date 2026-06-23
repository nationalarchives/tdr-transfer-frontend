package services

import configuration.ApplicationConfig
import io.circe.generic.auto._
import uk.gov.nationalarchives.aws.utils.s3.S3Clients._
import uk.gov.nationalarchives.aws.utils.s3.S3Utils
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusActions
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusActions.StatusAction
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusTypes.toStatusType
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues.StatusValue

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
    messageKey: String,
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
    val statusName = Option(failureMessages.getProperty(status.statusName)).getOrElse(status.statusName)
    val actionTypeLabel = Option(failureMessages.getProperty(statusAction.actionType.toString)).getOrElse(statusAction.actionType.toString)
    FileCheckStatusAction(
      statusName = statusName,
      actionType = actionTypeLabel,
      messageKey = statusAction.messageKey,
      message = Option(failureMessages.getProperty(statusAction.messageKey)).getOrElse(statusAction.messageKey)
    )
  }
}

class FileCheckFailureService @Inject() (val applicationConfig: ApplicationConfig)(implicit val ec: ExecutionContext) {

  def getFileCheckFailures(consignmentId: UUID): Future[List[FileCheckFailure]] =
    getFileCheckFailures(consignmentId, S3Utils(s3Async(applicationConfig.s3Endpoint)))

  def getFileCheckFailures(consignmentId: UUID, s3Utils: S3Utils): Future[List[FileCheckFailure]] = Future {
    blocking {
      val bucket = applicationConfig.transferErrorsS3BucketName
      val prefix = s"$consignmentId/filechecks/"
      val objects = s3Utils.listAllObjectsWithPrefix(bucket, prefix)
      objects.map { s3Object =>
        val fileCheckError = s3Utils.decodeS3JsonObject[FileCheckError](bucket, s3Object.key())
        val matches = fileCheckError.file.fileCheckResults.fileFormat.flatMap(_.matches)
        val statusActions = fileCheckError.statuses.flatMap { status =>
          Try(toStatusType(status.statusName)).toOption.flatMap { statusType =>
            StatusActions
              .action(statusType, StatusValue(status.statusValue))
              .map(FileCheckFailureService.resolveMessage(status, _))
          }
        }
        FileCheckFailure(
          originalPath = fileCheckError.file.originalPath,
          filename = fileCheckError.file.originalPath.split('/').last,
          matches = matches,
          statusActions = statusActions
        )
      }
    }
  }
}
