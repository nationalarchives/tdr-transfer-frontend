package controllers.util

import com.google.inject.Inject
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import controllers.{FileChecksProgress, TransferProgress}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import services.ConsignmentStatusService
import services.Statuses._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class TransferState @Inject()(val statusService: ConsignmentStatusService)(implicit val ec: ExecutionContext) {
  def getFileChecksProgress(consignmentId: UUID, token: BearerAccessToken): Future[FileChecksProgress] = {
    for {
      statuses <- statusService.getConsignmentStatuses(consignmentId, token)
    } yield toFileChecksProgress(statuses)
  }

  private def toFileChecksProgress(statuses: List[ConsignmentStatuses]) = {
    val fileCheckStatuses = getFileCheckStatusValues(statuses)
    val checksCompleted = fileChecksCompleted(fileCheckStatuses)
    val checksSucceeded = allChecksSucceeded(fileCheckStatuses)
    FileChecksProgress(allChecksSucceeded = checksSucceeded, allChecksCompleted = checksCompleted)
  }

  def getTransferProgress(consignmentId: UUID, token: BearerAccessToken): Future[TransferProgress] = {
    for {
      statuses <- statusService.getConsignmentStatuses(consignmentId, token)
      fileChecksProgress = toFileChecksProgress(statuses)
      result = fileChecksProgress match {
        case progress if progress.allChecksCompleted && progress.allChecksSucceeded =>
          val exportStatus = getStatusValues(statuses, Set(ExportType)).values.headOption.flatten
          TransferProgress(CompletedValue.value, exportStatus.getOrElse(""))
        case progress if progress.allChecksCompleted && !progress.allChecksSucceeded => TransferProgress(CompletedWithIssuesValue.value)
        case _ => TransferProgress(InProgressValue.value)
      }
    } yield result
  }

  private def fileChecksCompleted(fileCheckStatuses: Map[StatusType, Option[String]]): Boolean = {
    val clientChecksStatus = fileCheckStatuses(ClientChecksType).getOrElse("")
    clientChecksStatus.nonEmpty && clientChecksStatus != InProgressValue.value
  }

  private def allChecksSucceeded(fileCheckStatuses: Map[StatusType, Option[String]]): Boolean = {
    val statusValues = fileCheckStatuses.values.flatten
    statusValues.size == clientChecksStatuses.size && statusValues.forall(_ == CompletedValue.value)
  }

  private def getFileCheckStatusValues(statuses: List[ConsignmentStatuses]): Map[StatusType, Option[String]] = {
    getStatusValues(statuses, clientChecksStatuses)
  }

  private def getStatusValues(statuses: List[ConsignmentStatuses], statusTypes: Set[StatusType]): Map[StatusType, Option[String]] = {
    statusTypes.map(t => {
        val value = statuses.find(_.statusType == t.id).map(_.value)
        t -> value
      })
      .toMap
  }
}
