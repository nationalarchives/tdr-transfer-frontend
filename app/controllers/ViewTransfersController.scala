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

  private val inProgressStepStatus = "InProgress"
  private val failedStepStatus = "Failed"
  private val completedWithIssuesStepStatus = "CompletedWithIssues"
  private val completedStepStatus = "Completed"

  private val inProgressConsignment = "In Progress"
  private val failedConsignment = "Failed"
  private val transferredConsignment = "Transferred"

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

  val statusColours: Map[String, String] = Map(inProgressConsignment -> "yellow", failedConsignment -> "red", contactUs -> "red", transferredConsignment -> "green")

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
      case s if s.series.isEmpty => UserAction(inProgressConsignment, pageNameToUrlMap("series"), resumeTransfer)
      case s if s.series.contains(completedStepStatus) && s.transferAgreement.isEmpty =>
        UserAction(inProgressConsignment, pageNameToUrlMap("taPrivateBeta"), resumeTransfer)

      case s if s.transferAgreement.contains(inProgressStepStatus)                    => UserAction(inProgressConsignment, pageNameToUrlMap("taCompliance"), resumeTransfer)
      case s if s.transferAgreement.contains(completedStepStatus) && s.upload.isEmpty => UserAction(inProgressConsignment, pageNameToUrlMap("upload"), resumeTransfer)

      case s if s.upload.contains(inProgressStepStatus) || (s.upload.contains(completedStepStatus) && s.clientChecks.isEmpty) =>
        UserAction(inProgressConsignment, pageNameToUrlMap("upload"), resumeTransfer)
      case s if s.upload.contains(completedWithIssuesStepStatus) || s.upload.contains(failedStepStatus) =>
        UserAction(failedConsignment, pageNameToUrlMap("upload"), viewErrors)
      case s if s.upload.contains(completedStepStatus) && s.clientChecks.isEmpty => UserAction(inProgressConsignment, pageNameToUrlMap("fileChecks"), resumeTransfer)

      case s if s.clientChecks.contains(inProgressStepStatus) => UserAction(inProgressConsignment, pageNameToUrlMap("fileChecks"), resumeTransfer)
      case s if s.clientChecks.contains(completedWithIssuesStepStatus) || s.clientChecks.contains(failedStepStatus) =>
        UserAction(failedConsignment, pageNameToUrlMap("fileChecksResults"), viewErrors)
      case s if s.clientChecks.contains(completedStepStatus) && s.confirmTransfer.isEmpty =>
        UserAction(inProgressConsignment, pageNameToUrlMap("fileChecksResults"), resumeTransfer)

      case s if s.confirmTransfer.contains(completedStepStatus) && s.`export`.isEmpty => UserAction(inProgressConsignment, pageNameToUrlMap("fileChecksResults"), resumeTransfer)

      case s if s.`export`.contains(inProgressStepStatus) => UserAction(inProgressConsignment, pageNameToUrlMap("export"), downloadReport)
      case s if s.`export`.contains(completedStepStatus)  => UserAction(transferredConsignment, pageNameToUrlMap("export"), downloadReport)
      case s if s.`export`.contains(failedStepStatus)     => UserAction(failedConsignment, s"""mailto:%s?subject=Ref: $consignmentRef - Export failure""", "Contact us")

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
