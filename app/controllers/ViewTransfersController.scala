package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node.CurrentStatus
import graphql.codegen.types.ConsignmentFilters
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class ViewTransfersController @Inject() (val consignmentService: ConsignmentService, val keycloakConfiguration: KeycloakConfiguration, val controllerComponents: SecurityComponents)
    extends TokenSecurity {

  private val inProgressStep = "InProgress"
  private val failedStep = "Failed"
  private val completedWithIssuesStep = "CompletedWithIssues"
  private val completedStep = "Completed"

  private val transferStatusInProgress = "In Progress"
  private val transferStatusFailed = "Failed"
  private val transferStatusTransferred = "Transferred"

  private val resumeTransfer = "Resume transfer"
  private val viewErrors = "View errors"
  private val contactUs = "Contact us"
  private val downloadReport = "Download report"
  private val view = "View"

  private def standardTransferPageNameToUrl(consignmentId: UUID) = Map(
    "series" -> routes.SeriesDetailsController.seriesDetails(consignmentId).url,
    "taPrivateBeta" -> routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId).url,
    "taCompliance" -> routes.TransferAgreementComplianceController.transferAgreement(consignmentId).url,
    "upload" -> routes.UploadController.uploadPage(consignmentId).url,
    "fileChecks" -> routes.FileChecksController.fileChecksPage(consignmentId).url,
    "fileChecksResults" -> routes.FileChecksResultsController.fileCheckResultsPage(consignmentId).url,
    "export" -> routes.DownloadMetadataController.downloadMetadataCsv(consignmentId).url
  )

  private def judgmentTransferPageNameToUrl(consignmentId: UUID) = Map(
    "beforeYouUpload" -> routes.BeforeUploadingController.beforeUploading(consignmentId).url,
    "upload" -> routes.UploadController.judgmentUploadPage(consignmentId).url,
    "fileChecks" -> routes.FileChecksController.judgmentFileChecksPage(consignmentId).url,
    "fileChecksResults" -> routes.FileChecksResultsController.judgmentFileCheckResultsPage(consignmentId).url,
    "export" -> routes.TransferCompleteController.judgmentTransferComplete(consignmentId).url
  )

  val statusColours: Map[String, String] = Map(transferStatusInProgress -> "yellow", transferStatusFailed -> "red", contactUs -> "red", transferStatusTransferred -> "green")

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

  // scalastyle:off cyclomatic.complexity
  private def contactUsAction(consignmentRef: String, consignmentType: String = "standard"): UserAction = {
    val emailTitle = if (consignmentType == "judgment") "Judgment Transfer Failure (Status value is not valid)" else "Consignment Failure (Status value is not valid)"
    UserAction(contactUs, s"mailto:%s?subject=Ref: $consignmentRef - $emailTitle", contactUs)
  }

  private def convertStandardStatusesToUserActions(statuses: CurrentStatus, consignmentId: UUID, consignmentRef: String, consignmentType: String): UserAction = {
    val pageNameToUrlMap: Map[String, String] = standardTransferPageNameToUrl(consignmentId)

    statuses match {
      case s if s.series.isEmpty => UserAction(transferStatusInProgress, pageNameToUrlMap("series"), resumeTransfer)
      case s if s.series.contains(completedStep) && s.transferAgreement.isEmpty =>
        UserAction(transferStatusInProgress, pageNameToUrlMap("taPrivateBeta"), resumeTransfer)

      case s if s.transferAgreement.contains(inProgressStep)                    => UserAction(transferStatusInProgress, pageNameToUrlMap("taCompliance"), resumeTransfer)
      case s if s.transferAgreement.contains(completedStep) && s.upload.isEmpty => UserAction(transferStatusInProgress, pageNameToUrlMap("upload"), resumeTransfer)

      case s if s.confirmTransfer.isEmpty => convertUploadAndFileCheckStatusesToUserActions(s, pageNameToUrlMap, consignmentRef, consignmentType)
      case s if s.confirmTransfer.contains(completedStep) && s.`export`.isEmpty => UserAction(transferStatusInProgress, pageNameToUrlMap("fileChecksResults"), resumeTransfer)
      case s if s.`export`.nonEmpty                                             => convertExportStatusesToUserActions(s, pageNameToUrlMap, consignmentRef, consignmentType)

      case _ => contactUsAction(consignmentRef)
    }
  }

  private def convertJudgmentStatusesToUserActions(statuses: CurrentStatus, consignmentId: UUID, consignmentRef: String, consignmentType: String): UserAction = {
    val pageNameToUrlMap: Map[String, String] = judgmentTransferPageNameToUrl(consignmentId)

    statuses match {
      case s if s.upload.isEmpty    => UserAction(transferStatusInProgress, pageNameToUrlMap("beforeYouUpload"), resumeTransfer)
      case s if s.`export`.isEmpty  => convertUploadAndFileCheckStatusesToUserActions(s, pageNameToUrlMap, consignmentRef, consignmentType)
      case s if s.`export`.nonEmpty => convertExportStatusesToUserActions(s, pageNameToUrlMap, consignmentRef, consignmentType)

      case _ => contactUsAction(consignmentRef, consignmentType)
    }
  }

  private def fileChecksProgress(statuses: CurrentStatus, consignmentRef: String): String = {
    // println(s"Consignment Ref: $consignmentRef")
    // println(s"Statuses: ${statuses.toString}")
    val fileChecksStatuses = List(statuses.clientChecks, statuses.serverAntivirus, statuses.serverChecksum, statuses.serverFFID)
    // println(s"File statuses: ${fileChecksStatuses.toString()}")
    val x = fileChecksStatuses match {
      case fcs if fcs.forall(_.isEmpty)                                    => "BeforeFileChecks"
      case fcs if fcs.contains(Some(failedStep))                           => failedStep
      case fcs if fcs.contains(Some(completedWithIssuesStep))              => completedWithIssuesStep
      case fcs if fcs.contains(Some(inProgressStep)) || fcs.contains(None) => inProgressStep
      case fcs if fcs.forall(_.contains(completedStep))                    => completedStep
      case _                                                               => "XXXX"
    }
//    println(s"File checks: $x")
    x
  }

//  private def clientChecks(statuses: CurrentStatus): String ={
//    val clientChecks = statuses.clientChecks
//  }

  private def backendChecksStatus(statuses: CurrentStatus): String = {
    val backendChecksStatuses = List(statuses.serverAntivirus, statuses.serverChecksum, statuses.serverFFID)
    backendChecksStatuses match {
      // case bcs if bcs.forall(_.isEmpty) => "BeforeChecks"
      case bcs if bcs.forall(_.contains(completedStep)) => completedStep
      case bcs if bcs.contains(failedStep)              => failedStep
      case bcs if bcs.contains(completedWithIssuesStep) => completedWithIssuesStep
      case _                                            => inProgressStep
    }
  }

  private def convertUploadAndFileCheckStatusesToUserActions(
      statuses: CurrentStatus,
      pageNameToUrlMap: Map[String, String],
      consignmentRef: String,
      consignmentType: String
  ): UserAction = {
    val userAction = statuses match {
      case s if s.upload.contains(inProgressStep) || (s.upload.contains(completedStep) && s.clientChecks.isEmpty) =>
        UserAction(transferStatusInProgress, pageNameToUrlMap("upload"), resumeTransfer)
      case s if s.upload.contains(completedWithIssuesStep) || s.upload.contains(failedStep) =>
        UserAction(transferStatusFailed, pageNameToUrlMap("upload"), viewErrors)
      case s if s.upload.contains(completedStep) && s.clientChecks.isEmpty => UserAction(transferStatusInProgress, pageNameToUrlMap("fileChecks"), resumeTransfer)

      case s if s.clientChecks.contains(inProgressStep) => UserAction(transferStatusInProgress, pageNameToUrlMap("fileChecks"), resumeTransfer)
      case s if s.clientChecks.contains(completedWithIssuesStep) || s.clientChecks.contains(failedStep) =>
        UserAction(transferStatusFailed, pageNameToUrlMap("fileChecksResults"), viewErrors)
      case s if s.clientChecks.contains(completedStep) && s.confirmTransfer.isEmpty =>
        UserAction(transferStatusInProgress, pageNameToUrlMap("fileChecksResults"), resumeTransfer)
      case _ => contactUsAction(consignmentRef, consignmentType)
    }

    if (consignmentRef == "TEST-TDR-2022-GB4") {
      println(s"Consignment Ref: $consignmentRef")
      println(s"Current status: ${statuses.toString}")
      println(s"Backend checks: ${backendChecksStatus(statuses)}")
      println(s"User Action: ${userAction.toString}")
    }

    userAction
  }

  private def convertExportStatusesToUserActions(statuses: CurrentStatus, pageNameToUrlMap: Map[String, String], consignmentRef: String, consignmentType: String): UserAction = {
    val completeLink = if (consignmentType == "judgment") view else downloadReport

    statuses match {
      case s if s.`export`.contains(inProgressStep) => UserAction(transferStatusInProgress, pageNameToUrlMap("export"), completeLink)
      case s if s.`export`.contains(completedStep)  => UserAction(transferStatusTransferred, pageNameToUrlMap("export"), completeLink)
      case s if s.`export`.contains(failedStep)     => UserAction(transferStatusFailed, s"""mailto:%s?subject=Ref: $consignmentRef - Export failure""", "Contact us")
      case _                                        => contactUsAction(consignmentRef, consignmentType)
    }
  }
  // scalastyle:on cyclomatic.complexity

  private def createView(edges: Option[Edges]): Option[ConsignmentTransfers] =
    edges.map { edge =>
      val consignmentType = edge.node.consignmentType.get
      val conversionMethod: (CurrentStatus, UUID, String, String) => UserAction =
        if (consignmentType == "judgment") convertJudgmentStatusesToUserActions else convertStandardStatusesToUserActions

      val userActions: UserAction = conversionMethod(edge.node.currentStatus, edge.node.consignmentid.get, edge.node.consignmentReference, consignmentType)

      ConsignmentTransfers(
        edge.node.consignmentid,
        edge.node.consignmentReference,
        userActions.transferStatus,
        statusColours(userActions.transferStatus),
        userActions,
        edge.node.exportDatetime.map(_.format(formatter)).getOrElse("N/A"),
        edge.node.createdDatetime.map(_.format(formatter)).getOrElse(""),
        edge.node.totalFiles
      )
    }

  private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss")
}

case class ConsignmentTransfers(
    consignmentId: Option[UUID],
    reference: String,
    status: String,
    statusColour: String,
    userAction: UserAction,
    dateOfExport: String,
    dateStarted: String,
    numberOfFiles: Int
)

case class UserAction(transferStatus: String, actionUrl: String, actionText: String)
