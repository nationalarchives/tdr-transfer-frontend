package controllers.util

import com.google.inject.Inject
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import services.ConsignmentStatusService
import services.Statuses._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class TransferState @Inject() (val statusService: ConsignmentStatusService)(implicit val ec: ExecutionContext) {
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

  private def toTransferProgress(statuses: List[ConsignmentStatuses]) = {
    val fileChecksProgress = toFileChecksProgress(statuses)
    val exportStatus = getStatusValues(statuses, Set(ExportType)).values.headOption.flatten
    val transferComplete = fileChecksProgress match {
      case _ if fileChecksProgress.allChecksCompleted && (exportStatus.nonEmpty || !fileChecksProgress.allChecksSucceeded) => true
      case _                                                                                                               => false
    }
    val progress = fileChecksProgress match {
      case _ if fileChecksProgress.allChecksSucceeded                                           => CompletedValue
      case _ if fileChecksProgress.allChecksCompleted && !fileChecksProgress.allChecksSucceeded => CompletedWithIssuesValue
      case _                                                                                    => InProgressValue
    }
    TransferProgress(progress.value, transferComplete = transferComplete)
  }

  def getTransferProgress(consignmentId: UUID, token: BearerAccessToken): Future[TransferProgress] = {
    for {
      statuses <- statusService.getConsignmentStatuses(consignmentId, token)
    } yield toTransferProgress(statuses)
  }

  def getJudgmentTransferProgress(consignmentId: UUID, token: BearerAccessToken): Future[JudgmentTransferProgress] = {
    for {
      statuses <- statusService.getConsignmentStatuses(consignmentId, token)
      transferComplete = toTransferProgress(statuses).transferComplete
      allFileChecksSucceeded = toFileChecksProgress(statuses).allChecksSucceeded
    } yield JudgmentTransferProgress(transferComplete, allFileChecksSucceeded)
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
    statusTypes
      .map(t => {
        val value = statuses.find(_.statusType == t.id).map(_.value)
        t -> value
      })
      .toMap
  }
}

case class FileChecksProgress(allChecksSucceeded: Boolean, allChecksCompleted: Boolean)

case class TransferProgress(fileChecksStatus: String, transferComplete: Boolean)

case class JudgmentTransferProgress(transferComplete: Boolean, allFileChecksSucceeded: Boolean)
