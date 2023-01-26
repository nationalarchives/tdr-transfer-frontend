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

  private def pageNameToUrl(consignmentId: UUID) = Map(
    "series" -> routes.SeriesDetailsController.seriesDetails(consignmentId).url,
    "taPrivateBeta" -> routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId).url,
    "taCompliance" -> routes.TransferAgreementComplianceController.transferAgreement(consignmentId).url,
    "upload" -> routes.UploadController.uploadPage(consignmentId).url,
    "fileChecks" -> routes.FileChecksController.fileChecksPage(consignmentId).url,
    "fileChecksResults" -> routes.FileChecksResultsController.fileCheckResultsPage(consignmentId).url,
    "export" -> routes.DownloadMetadataController.downloadMetadataCsv(consignmentId).url
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
    } yield {
      val consignmentType = if (request.token.isJudgmentUser) "judgments" else "standard"
      Ok(views.html.viewTransfers(consignments, request.token.name, request.token.email, consignmentType))
    }
  }

  private def createView(edges: Option[Edges]): Option[ConsignmentTransfers] =
    edges.map { edge =>
      val userActions: UserAction = convertStandardStatusesToUserActions(edge.node.currentStatus, edge.node.consignmentid.get, edge.node.consignmentReference)

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

  // scalastyle:off cyclomatic.complexity
  private def convertStandardStatusesToUserActions(statuses: CurrentStatus, consignmentId: UUID, consignmentRef: String): UserAction = {
    val pageNameToUrlMap: Map[String, String] = pageNameToUrl(consignmentId)

    statuses match {
      case s if s.series.isEmpty => UserAction(transferStatusInProgress, pageNameToUrlMap("series"), resumeTransfer)
      case s if s.series.contains(completedStep) && s.transferAgreement.isEmpty =>
        UserAction(transferStatusInProgress, pageNameToUrlMap("taPrivateBeta"), resumeTransfer)

      case s if s.transferAgreement.contains(inProgressStep)                    => UserAction(transferStatusInProgress, pageNameToUrlMap("taCompliance"), resumeTransfer)
      case s if s.transferAgreement.contains(completedStep) && s.upload.isEmpty => UserAction(transferStatusInProgress, pageNameToUrlMap("upload"), resumeTransfer)

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

      case s if s.confirmTransfer.contains(completedStep) && s.`export`.isEmpty => UserAction(transferStatusInProgress, pageNameToUrlMap("fileChecksResults"), resumeTransfer)

      case s if s.`export`.contains(inProgressStep) => UserAction(transferStatusInProgress, pageNameToUrlMap("export"), downloadReport)
      case s if s.`export`.contains(completedStep)  => UserAction(transferStatusTransferred, pageNameToUrlMap("export"), downloadReport)
      case s if s.`export`.contains(failedStep)     => UserAction(transferStatusFailed, s"""mailto:%s?subject=Ref: $consignmentRef - Export failure""", "Contact us")

      case _ => UserAction(contactUs, s"""mailto:%s?subject=Ref: $consignmentRef - Consignment Failure (Status value is not valid)""", contactUs)
    }
  }
  // scalastyle:on cyclomatic.complexity
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
