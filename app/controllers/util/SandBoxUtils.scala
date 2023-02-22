package controllers.util

import controllers.{UserAction, routes}
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.CurrentStatus
import org.keycloak.representations.idm.UserRepresentation

import java.util.UUID

object SandBoxUtils {

  case class ConsignmentStatus(statusType: String, statusValue: String)

  implicit class CurrentStatusHelper(status: CurrentStatus) {}

  private def toExportUserAction(status: String, consignmentType: String, consignmentRef: String, consignmentId: UUID): UserAction = {
    val completeLink = if (consignmentType == "judgment") "View transfer" else "Download report"
    val url = if (consignmentType == "judgment") {
      routes.TransferCompleteController.judgmentTransferComplete(consignmentId).url
    } else {
      routes.DownloadMetadataController.downloadMetadataCsv(consignmentId).url
    }
    val transferStatus = if (status == "InProgress") { "In Progress" }
    else { "Transferred" }

    status match {
      case "InProgress" | "Completed" => UserAction(transferStatus, url, completeLink)
      case _                          => UserAction("Failed", s"""mailto:%s?subject=Ref: $consignmentRef - Export failure""", "Contact us")
    }
  }

  private def toClientChecksUserAction(status: String, consignmentType: String, consignmentRef: String): UserAction = {
    status match {
      case "Completed"  => UserAction("In Progress", "completedUrl", "Resume transfer")
      case "InProgress" => UserAction("In Progress", "inProgressUrl", "Resume transfer")
      case _            => UserAction("Failed", "failedUrl", "View errors")
    }
  }

  def toUploadUserAction(status: String, consignmentType: String, consignmentRef: String): UserAction = {
    status match {
      case "Completed" => UserAction("", "", "")
      case _           => UserAction("", "", "")
    }
  }

  private def toTransferAgreementUserAction(status: String, consignmentType: String, consignmentRef: String, consignmentId: UUID): UserAction = {
    status match {
      case "InProgress" => UserAction("In Progress", routes.TransferAgreementComplianceController.transferAgreement(consignmentId).url, "Resume transfer")
      case _            => UserAction("In Progress", routes.UploadController.uploadPage(consignmentId).url, "Resume transfer")
    }
  }

  private def toSeriesUserAction(status: String, consignmentType: String, consignmentRef: String, consignmentId: UUID): UserAction = {
    status match {
      case "Completed" => UserAction("In Progress", routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId).url, "Resume transfer")
      case _           => UserAction("", "", "")
    }
  }

  def x(status: CurrentStatus, consignmentType: String, consignmentRef: String, consignmentId: UUID): Unit = {
    status match {
      case s if s.`export`.nonEmpty => toExportUserAction(s.`export`.get, consignmentType, consignmentRef, consignmentId)
      case s if s.confirmTransfer.contains("Completed") =>
        UserAction("In Progress", routes.FileChecksResultsController.fileCheckResultsPage(consignmentId).url, "Resume transfer")
      case s if s.clientChecks.nonEmpty      => toClientChecksUserAction(s.clientChecks.get, consignmentType, consignmentRef)
      case s if s.upload.nonEmpty            => toUploadUserAction(s.upload.get, consignmentType, consignmentRef)
      case s if s.transferAgreement.nonEmpty => toTransferAgreementUserAction(s.transferAgreement.get, consignmentType, consignmentRef, consignmentId)
      case s if s.series.nonEmpty            => toSeriesUserAction(s.series.get, consignmentType, consignmentRef, consignmentId)
      case s if s.series.isEmpty || s.upload.isEmpty =>
        val url = if (consignmentType == "judgment") { routes.BeforeUploadingController.beforeUploading(consignmentId).url }
        else { routes.SeriesDetailsController.seriesDetails(consignmentId).url }
        UserAction("In progress", url, "Resume transfer")
      case _ => UserAction("Contact us", s"mailto:%s?subject=Ref: $consignmentRef - Issue With Transfer", "Contact us")
    }
  }
}
