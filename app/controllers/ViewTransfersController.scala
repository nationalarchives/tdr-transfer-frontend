package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.ConsignmentStatuses
import graphql.codegen.types.ConsignmentFilters
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses._
import services.ConsignmentService

import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class ViewTransfersController @Inject() (val consignmentService: ConsignmentService, val keycloakConfiguration: KeycloakConfiguration, val controllerComponents: SecurityComponents)
    extends TokenSecurity {

  private val statusColours: Map[String, String] = Map(InProgress.value -> "yellow", Failed.value -> "red", ContactUs.value -> "red", Transferred.value -> "green")
  private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")

  implicit class ConsignmentStatusesHelper(statuses: List[ConsignmentStatuses]) {
    def containsStatuses(statusTypes: StatusType*): Boolean = {
      statusTypes.foldLeft(false)((contains, statusType) => contains || statuses.exists(_.statusType == statusType.id))
    }

    def statusValue(statusType: StatusType): Option[String] = {
      statuses.find(_.statusType == statusType.id).map(_.value)
    }

    def filterNonJudgmentStatuses: List[ConsignmentStatuses] = {
      statuses.map(s => toStatusType(s.statusType) -> s).filterNot(_._1.nonJudgmentStatus).map(_._2)
    }
  }

  def viewConsignments(): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    val consignmentFilters = ConsignmentFilters(Some(request.token.userId), None)
    for {
      consignmentTransfers <- consignmentService.getConsignments(consignmentFilters, request.token.bearerAccessToken)
      consignments = consignmentTransfers.edges match {
        case Some(edges) => edges.flatMap(createView)
        case None        => Nil
      }
    } yield Ok(views.html.viewTransfers(consignments, request.token.name, request.token.email, request.token.isJudgmentUser))
  }

  private def createView(edges: Option[Edges]): Option[ConsignmentTransfers] =
    edges.map { edge =>
      val userAction: UserAction = toUserAction(edge.node)

      ConsignmentTransfers(
        edge.node.consignmentid,
        edge.node.consignmentReference,
        userAction.transferStatus,
        statusColours(userAction.transferStatus),
        userAction,
        edge.node.exportDatetime.map(_.format(formatter)).getOrElse("N/A"),
        edge.node.createdDatetime.map(_.format(formatter)).getOrElse(""),
        edge.node.totalFiles
      )
    }

  private def toUserAction(consignment: Node): UserAction = {
    val judgmentType = consignment.consignmentType.contains("judgment")
    val consignmentId = consignment.consignmentid.get
    val consignmentRef = consignment.consignmentReference
    val statuses = consignment.consignmentStatuses

    val statusesToCheck: List[ConsignmentStatuses] = if (judgmentType) {
      statuses.filterNonJudgmentStatuses
    } else {
      statuses
    }

    statusesToCheck match {
      case s if s.containsStatuses(ExportType) => toExportAction(s.find(_.statusType == ExportType.id).get, judgmentType, consignmentId, consignmentRef)
      case s if s.statusValue(ConfirmTransferType).contains(CompletedValue.value) =>
        UserAction(InProgress.value, routes.TransferCompleteController.transferComplete(consignmentId).url, Resume.value)
      case s if additionalMetadataEntered(s) =>
        UserAction(InProgress.value, routes.AdditionalMetadataController.start(consignmentId).url, Resume.value)
      case s if s.containsStatuses(ServerAntivirusType, ServerChecksumType, ServerFFIDType) =>
        toFileChecksAction(s, judgmentType, consignmentId)
      case s if s.containsStatuses(ClientChecksType, UploadType) => toClientSideChecksAction(statuses, consignmentId, judgmentType)
      case s if s.containsStatuses(TransferAgreementType) =>
        toTransferAgreementAction(s.find(_.statusType == TransferAgreementType.id).get, consignmentId)
      case s if s.statusValue(SeriesType).contains(CompletedValue.value) =>
        UserAction(InProgress.value, routes.TransferAgreementPart1Controller.transferAgreement(consignmentId).url, Resume.value)
      case s if s.isEmpty => toStartAction(consignmentId, judgmentType)
      case _              => toContactUsAction(consignmentRef)
    }
  }

  private def toExportAction(status: ConsignmentStatuses, judgmentType: Boolean, consignmentId: UUID, consignmentRef: String): UserAction = {
    val actionText = if (judgmentType) View.value else Download.value
    val url =
      if (judgmentType) routes.TransferCompleteController.judgmentTransferComplete(consignmentId).url
      else routes.DownloadMetadataController.downloadMetadataCsv(consignmentId).url
    status.value match {
      case s if s == InProgressValue.value => UserAction(InProgress.value, url, actionText)
      case s if s == CompletedValue.value  => UserAction(Transferred.value, url, actionText)
      case s if s == FailedValue.value     => UserAction(Failed.value, s"""mailto:%s?subject=Ref: $consignmentRef - Export failure""", ContactUs.value)
      case _                               => toContactUsAction(consignmentRef)
    }
  }

  private def toContactUsAction(consignmentRef: String): UserAction = {
    UserAction(ContactUs.value, s"mailto:%s?subject=Ref: $consignmentRef - Issue With Transfer", ContactUs.value)
  }

  private def additionalMetadataEntered(statuses: List[ConsignmentStatuses]): Boolean = {
    val additionalMetadataStatuses = statuses.filter(s => s.statusType == DescriptiveMetadataType.id || s.statusType == ClosureMetadataType.id).map(_.value)
    !additionalMetadataStatuses.forall(_ == NotEnteredValue.value)
  }

  private def toFileChecksAction(statuses: List[ConsignmentStatuses], judgmentType: Boolean, consignmentId: UUID): UserAction = {
    val checksUrl = if (judgmentType) {
      routes.FileChecksController.judgmentFileChecksPage(consignmentId).url
    } else {
      routes.FileChecksController.fileChecksPage(consignmentId).url
    }

    val resultsUrl = if (judgmentType) {
      routes.FileChecksResultsController.judgmentFileCheckResultsPage(consignmentId).url
    } else {
      routes.FileChecksResultsController.fileCheckResultsPage(consignmentId).url
    }

    val fileChecksStatuses: List[String] =
      statuses.filter(s => s.statusType == ServerAntivirusType.id || s.statusType == ServerChecksumType.id || s.statusType == ServerFFIDType.id).map(_.value)
    fileChecksStatuses match {
      case fcs if fcs.contains(FailedValue.value) || fcs.contains(CompletedWithIssuesValue.value) =>
        UserAction(Failed.value, resultsUrl, Errors.value)
      case fcs if fcs.contains(InProgressValue.value) || fcs.size < 3 =>
        UserAction(InProgress.value, checksUrl, Resume.value)
      case _ =>
        UserAction(InProgress.value, resultsUrl, Resume.value)
    }
  }

  private def toClientSideChecksAction(statuses: List[ConsignmentStatuses], consignmentId: UUID, judgmentType: Boolean): UserAction = {
    val uploadUrl = if (judgmentType) {
      routes.UploadController.judgmentUploadPage(consignmentId).url
    } else {
      routes.UploadController.uploadPage(consignmentId).url
    }

    val checksUrl = if (judgmentType) {
      routes.FileChecksController.judgmentFileChecksPage(consignmentId).url
    } else {
      routes.FileChecksController.fileChecksPage(consignmentId).url
    }

    val checkStatuses = statuses.filter(s => s.statusType == ClientChecksType.id || s.statusType == UploadType.id)
    val checkValues = checkStatuses.map(_.value)
    val abandoned = checkValues.size == 2 && checkValues.forall(_ == InProgressValue.value) && checkStatuses.flatMap(_.modifiedDatetime).isEmpty

    checkValues match {
      case csc if csc.contains(FailedValue.value) || csc.contains(CompletedWithIssuesValue.value) || abandoned =>
        UserAction(Failed.value, uploadUrl, Errors.value)
      case csc if csc.contains(InProgressValue.value) || csc.size < 2 =>
        UserAction(InProgress.value, uploadUrl, Resume.value)
      case _ =>
        UserAction(InProgress.value, checksUrl, Resume.value)
    }
  }

  private def toTransferAgreementAction(status: ConsignmentStatuses, consignmentId: UUID): UserAction = {
    status.value match {
      case v if v == InProgressValue.value =>
        UserAction(InProgress.value, routes.TransferAgreementPart2Controller.transferAgreement(consignmentId).url, Resume.value)
      case _ =>
        UserAction(InProgress.value, routes.UploadController.uploadPage(consignmentId).url, Resume.value)
    }
  }

  private def toStartAction(consignmentId: UUID, judgmentType: Boolean): UserAction = {
    val startUrl = if (judgmentType) {
      routes.BeforeUploadingController.beforeUploading(consignmentId).url
    } else {
      routes.SeriesDetailsController.seriesDetails(consignmentId).url
    }
    UserAction(InProgress.value, startUrl, Resume.value)
  }
}

case class ConsignmentTransfers(
    consignmentId: Option[UUID],
    reference: String,
    status: String,
    statusColour: String,
    userAction: UserAction,
    dateOfTransfer: String,
    dateStarted: String,
    numberOfFiles: Int
)

case class UserAction(transferStatus: String, actionUrl: String, actionText: String)

sealed trait ActionText {
  val value: String
}

sealed trait TransferStatus {
  val value: String
}

case object Errors extends ActionText {
  val value: String = "View errors"
}

case object View extends ActionText {
  val value: String = "View"
}

case object Download extends ActionText {
  val value: String = "Download report"
}

case object Resume extends ActionText {
  val value: String = "Resume transfer"
}

case object ContactUs extends ActionText with TransferStatus {
  val value: String = "Contact us"
}

case object InProgress extends TransferStatus {
  val value: String = "In Progress"
}

case object Failed extends TransferStatus {
  val value: String = "Failed"
}

case object Transferred extends TransferStatus {
  val value: String = "Transferred"
}
