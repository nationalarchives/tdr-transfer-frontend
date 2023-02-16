package controllers.util

import controllers.{UserAction, routes}
import controllers.util.TransferProgressUtils._
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.CurrentStatus

import java.util.UUID

class TransferProgressUtils() {
  private val inProgress: String = "InProgress"
  private val completed: String = "Completed"
  private val completedWithIssues: String = "CompletedWithIssues"
  private val failed: String = "Failed"

  private val transferStatusInProgress = "In Progress"
  private val transferStatusFailed = "Failed"
  private val transferStatusTransferred = "Transferred"

  private val resumeTransfer = "Resume transfer"
  private val viewErrors = "View errors"
  private val contactUs = "Contact us"
  private val downloadReport = "Download report"
  private val view = "View"

  private def fileChecksProgress(statuses: CurrentStatus): String = {
    val fileChecksStatuses = Set(statuses.serverAntivirus, statuses.serverChecksum, statuses.serverFFID)
    fileChecksStatuses match {
      case fcs if fcs.contains(Some(failed))                           => failed
      case fcs if fcs.contains(Some(completedWithIssues))              => completedWithIssues
      case fcs if fcs.contains(Some(inProgress)) || fcs.contains(None) => inProgress
      case _                                                           => completed
    }
  }

  def toTransferState(statuses: CurrentStatus, consignmentType: String): TransferState = {
    val isStandard: Boolean = consignmentType == "standard"
    statuses match {
      case s if s.series.isEmpty && isStandard                                                                                      => SeriesInProgress
      case s if s.series.contains(completed) && s.transferAgreement.isEmpty && isStandard                                           => SeriesCompleted
      case s if s.transferAgreement.contains(inProgress) && isStandard                                                              => TransferAgreementInProgress
      case s if s.transferAgreement.contains(completed) && s.upload.isEmpty && isStandard                                           => TransferAgreementCompleted
      case s if s.upload.isEmpty && !isStandard                                                                                     => BeforeUpload
      case s if s.upload.contains(inProgress) && s.clientChecks.isEmpty                                                             => UploadInProgress
      case s if s.upload.contains(completedWithIssues) || s.upload.contains(failed)                                                 => UploadFailed
      case s if s.upload.contains(completed) && s.clientChecks.isEmpty                                                              => UploadCompleted
      case s if s.clientChecks.contains(inProgress)                                                                                 => ClientChecksInProgress
      case s if s.clientChecks.contains(completedWithIssues) || s.clientChecks.contains(failed)                                     => ClientChecksFailed
      case s if s.clientChecks.contains(completed) && s.serverAntivirus.isEmpty && s.serverChecksum.isEmpty && s.serverFFID.isEmpty => ClientChecksCompleted
      case s if fileChecksProgress(s) == inProgress                                                                                 => FileChecksInProgress
      case s if fileChecksProgress(s) == completed && s.confirmTransfer.isEmpty                                                     => FileChecksCompleted
      case s if fileChecksProgress(s) == completedWithIssues || fileChecksProgress(s) == failed                                     => FileChecksFailed
      case s if s.confirmTransfer.contains(completed) && s.`export`.isEmpty                                                         => ConfirmTransferCompleted
      case s if s.`export`.contains(inProgress)                                                                                     => ExportInProgress
      case s if s.`export`.contains(completed)                                                                                      => ExportCompleted
      case s if s.`export`.contains(failed)                                                                                         => ExportFailed
      case _                                                                                                                        => NotRecognised
    }
  }

  private def contactUsAction(consignmentRef: String, consignmentType: String = "standard"): UserAction = {
    val emailTitle = if (consignmentType == "judgment") "Judgment Transfer Issue" else "Consignment Issue"
    UserAction(contactUs, s"mailto:%s?subject=Ref: $consignmentRef - $emailTitle", contactUs)
  }

  def transferStateToStandardAction(state: TransferState, consignmentId: UUID, consignmentRef: String, consignmentType: String): UserAction = {
    val isJudgment = consignmentType == "judgment"
    lazy val uploadUrl = if (isJudgment) { routes.UploadController.judgmentUploadPage(consignmentId).url }
    else { routes.UploadController.uploadPage(consignmentId).url }
    lazy val fileChecksUrl = if (isJudgment) { routes.FileChecksController.judgmentFileChecksPage(consignmentId).url }
    else { routes.FileChecksController.fileChecksPage(consignmentId).url }
    lazy val fileChecksResultsUrl = if (isJudgment) {
      routes.FileChecksResultsController.judgmentFileCheckResultsPage(consignmentId).url
    } else {
      routes.FileChecksResultsController.fileCheckResultsPage(consignmentId).url
    }
    lazy val exportUrl = if (isJudgment) { routes.TransferCompleteController.judgmentTransferComplete(consignmentId).url }
    else { routes.DownloadMetadataController.downloadMetadataCsv(consignmentId).url }
    lazy val exportActionText = if (isJudgment) view else downloadReport

    state match {
      case SeriesInProgress            => UserAction(transferStatusInProgress, routes.SeriesDetailsController.seriesDetails(consignmentId).url, resumeTransfer)
      case SeriesCompleted             => UserAction(transferStatusInProgress, routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId).url, resumeTransfer)
      case TransferAgreementInProgress => UserAction(transferStatusInProgress, routes.TransferAgreementComplianceController.transferAgreement(consignmentId).url, resumeTransfer)
      case TransferAgreementCompleted  => UserAction(transferStatusInProgress, routes.UploadController.uploadPage(consignmentId).url, resumeTransfer)
      case BeforeUpload                => UserAction(transferStatusInProgress, routes.BeforeUploadingController.beforeUploading(consignmentId).url, resumeTransfer)
      case UploadInProgress | UploadCompleted                                    => UserAction(transferStatusInProgress, uploadUrl, resumeTransfer)
      case UploadFailed                                                          => UserAction(transferStatusFailed, uploadUrl, resumeTransfer)
      case ClientChecksInProgress | ClientChecksCompleted | FileChecksInProgress => UserAction(transferStatusInProgress, fileChecksUrl, resumeTransfer)
      case ClientChecksFailed                                                    => UserAction(transferStatusFailed, fileChecksUrl, viewErrors)
      case FileChecksCompleted                                                   => UserAction(transferStatusInProgress, fileChecksResultsUrl, resumeTransfer)
      case FileChecksFailed                                                      => UserAction(transferStatusFailed, fileChecksResultsUrl, viewErrors)
      case ConfirmTransferCompleted                                              => UserAction(transferStatusInProgress, fileChecksResultsUrl, resumeTransfer)
      case ExportInProgress =>
        val transferStatus = if (isJudgment) { transferStatusTransferred }
        else { transferStatusInProgress }
        UserAction(transferStatus, exportUrl, exportActionText)
      case ExportCompleted => UserAction(transferStatusTransferred, exportUrl, exportActionText)
      case ExportFailed    => UserAction(transferStatusFailed, s"""mailto:%s?subject=Ref: $consignmentRef - Export failure""", "Contact us")
      case _               => contactUsAction(consignmentRef, consignmentType)
    }
  }
}

object TransferProgressUtils {
  sealed trait TransferState
  case object SeriesInProgress extends TransferState
  case object SeriesCompleted extends TransferState
  case object TransferAgreementInProgress extends TransferState
  case object TransferAgreementCompleted extends TransferState
  case object BeforeUpload extends TransferState
  case object UploadInProgress extends TransferState
  case object UploadFailed extends TransferState
  case object UploadCompleted extends TransferState
  case object ClientChecksInProgress extends TransferState
  case object ClientChecksCompleted extends TransferState
  case object ClientChecksFailed extends TransferState
  case object FileChecksInProgress extends TransferState
  case object FileChecksCompleted extends TransferState
  case object FileChecksFailed extends TransferState
  case object ExportInProgress extends TransferState
  case object ExportCompleted extends TransferState
  case object ExportFailed extends TransferState
  case object NotRecognised extends TransferState
  case object ConfirmTransferCompleted extends TransferState
}
