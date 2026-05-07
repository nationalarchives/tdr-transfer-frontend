package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import controllers.MetadataReviewActionController._
import controllers.util.{DateUtils, DropdownField, InputNameAndValue}
import graphql.codegen.types.ConsignmentStatusInput
import org.pac4j.play.scala.SecurityComponents
import play.api.Logging
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.MessagingService.MetadataReviewSubmittedEvent
import services.Statuses._
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}
import uk.gov.nationalarchives.tdr.common.utils.statuses.MetadataReviewLogAction.{Approval, Rejection, Submission}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MetadataReviewActionController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService,
    val messagingService: MessagingService,
    val applicationConfig: ApplicationConfig
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport
    with Logging {

  private val statusReasonMaxLength = 1300

  private val selectedDecisionForm: Form[SelectedStatusData] = Form(
    mapping(
      "status" -> text.verifying("Select a status", t => t.nonEmpty),
      "statusReason" -> optional(text.verifying(s"Reason for status change must be $statusReasonMaxLength characters or less", s => s.length <= statusReasonMaxLength))
    )(SelectedStatusData.apply)(SelectedStatusData.unapply)
  )

  def consignmentMetadataDetails(consignmentId: UUID): Action[AnyContent] = tnaUserAction { implicit request: Request[AnyContent] =>
    getConsignmentMetadataDetails(consignmentId, request, Ok, selectedDecisionForm)
  }

  def submitReview(consignmentId: UUID, consignmentRef: String, userEmail: String): Action[AnyContent] = tnaUserAction { implicit request: Request[AnyContent] =>
    val formValidationResult: Form[SelectedStatusData] = selectedDecisionForm.bindFromRequest()
    val errorFunction: Form[SelectedStatusData] => Future[Result] = { formWithErrors: Form[SelectedStatusData] =>
      getConsignmentMetadataDetails(consignmentId, request, BadRequest, formWithErrors)
    }

    val successFunction: SelectedStatusData => Future[Result] = { formData: SelectedStatusData =>
      logger.info(s"TNA user: ${request.token.userId} has set consignment: $consignmentId to ${formData.statusId}")
      for {
        consignmentDetails <- consignmentService.getConsignmentDetailForMetadataReviewRequest(consignmentId, request.token.bearerAccessToken)
        _ <- Future.sequence(
          consignmentStatusUpdates(formData).map { case (statusType, statusValue) =>
            val notes = if (!applicationConfig.blockMetadataReviewV2 && statusType == MetadataReviewType.id) formData.statusReason else None
            consignmentStatusService.updateConsignmentStatus(
              ConsignmentStatusInput(consignmentId, statusType, Some(statusValue), None, notes),
              request.token.bearerAccessToken
            )
          }
        )
      } yield {
        messagingService.sendMetadataReviewSubmittedNotification(
          MetadataReviewSubmittedEvent(
            environment = applicationConfig.frontEndInfo.stage,
            consignmentReference = consignmentDetails.consignmentReference,
            urlLink = generateUrlLink(request, routes.MetadataReviewStatusController.metadataReviewStatusPage(consignmentId).url),
            userEmail = userEmail,
            status = formData.statusId,
            transferringBodyName = consignmentDetails.transferringBodyName,
            seriesCode = consignmentDetails.seriesName,
            userId = request.token.userId.toString,
            closedRecords = consignmentDetails.totalClosedRecords > 0,
            totalRecords = consignmentDetails.totalFiles
          )
        )
        if (applicationConfig.blockMetadataReviewV2)
          Redirect(routes.MetadataReviewController.metadataReviews())
        else
          Redirect(routes.MetadataReviewActionController.consignmentMetadataDetails(consignmentId))
            .flashing("success" -> "true")
      }
    }

    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }

  private def getConsignmentMetadataDetails(consignmentId: UUID, request: Request[AnyContent], status: Status, form: Form[SelectedStatusData])(implicit
      requestHeader: RequestHeader
  ): Future[Result] = {
    if (applicationConfig.blockMetadataReviewV2) {
      for {
        consignment <- consignmentService.getConsignmentDetailForMetadataReview(consignmentId, request.token.bearerAccessToken)
        userDetails <- keycloakConfiguration.userDetails(consignment.userid.toString)
      } yield {
        status(
          views.html.tna.metadataReviewAction(
            consignmentId,
            consignment,
            userDetails.email,
            createDropDownField(List(InputNameAndValue(ApproveLabel, CompletedValue.value), InputNameAndValue(RejectLabel, CompletedWithIssuesValue.value)), form),
            request.token.isTransferAdviser
          )
        )
      }
    } else {
      for {
        consignment <- consignmentService.getConsignmentDetailForMetadataReview(consignmentId, request.token.bearerAccessToken)
        action = consignment.metadataReviewLogs.lastOption.map(_.action)
        totalSubmissions = consignment.metadataReviewLogs.count(_.action == Submission.value)
        dateSubmitted = consignment.metadataReviewLogs.filter(_.action == Submission.value).lastOption.map(log => DateUtils.formatWithDaySuffix(log.eventTime)).getOrElse("Unknown")
        userDetails <- keycloakConfiguration.userDetails(consignment.userid.toString)
        lastReviewLog = consignment.metadataReviewLogs.filter(log => log.action == Approval.value || log.action == Rejection.value).lastOption
        lastReviewerDetails <- lastReviewLog match {
          case Some(log) => keycloakConfiguration.userDetails(log.userId.toString).map(u => Some(u))
          case None      => Future.successful(None)
        }
        lastReviewedByName = lastReviewerDetails.map(d => s"${d.firstName} ${d.lastName}")
        lastReviewedByEmail = lastReviewerDetails.map(_.email)
        lastUpdated = lastReviewLog.map(log => DateUtils.formatWithDaySuffix(log.eventTime))
        lastNote = lastReviewLog.flatMap(_.metadataReviewNotes)
      } yield {
        status(
          views.html.tna.metadataReviewActionV2(
            consignmentId,
            consignment,
            action.getOrElse("Unknown"),
            totalSubmissions,
            dateSubmitted,
            lastReviewedByName,
            lastReviewedByEmail,
            lastUpdated,
            lastNote,
            userDetails.email,
            createDropDownField(List(InputNameAndValue(ApprovedLabel, CompletedValue.value), InputNameAndValue(RejectedLabel, CompletedWithIssuesValue.value)), form),
            form("statusReason").errors.flatMap(_.messages),
            request.token.isTransferAdviser,
            request.flash.get("success").isDefined
          )
        )
      }
    }
  }

  private def createDropDownField(options: List[InputNameAndValue], form: Form[SelectedStatusData]): DropdownField = {
    val errors = form("status").errors.headOption match {
      case Some(formError) => formError.messages
      case None            => Nil
    }
    DropdownField(
      form("status").id,
      fieldName = "",
      fieldAlternativeName = "",
      fieldDescription = "",
      fieldInsetTexts = Nil,
      multiValue = false,
      options,
      selectedOption = None,
      isRequired = true,
      errors.toList
    )
  }


  private def generateUrlLink(request: Request[AnyContent], route: String): String = {
    val baseUrl = if (request.secure) "https" else "http"
    baseUrl + "://" + request.host + route
  }
}

object MetadataReviewActionController {
  val ApproveLabel = "Approve"
  val RejectLabel = "Reject"
  val ApprovedLabel = "Approved"
  val RejectedLabel = "Rejected"

  def consignmentStatusUpdates(formData: SelectedStatusData): Seq[(String, String)] = {
    val metadataReviewStatusUpdate = (MetadataReviewType.id, formData.statusId)
    val metadataStatusResets =
      if (formData.statusId == CompletedWithIssuesValue.value)
        Seq((DraftMetadataType.id, InProgressValue.value))
      else Seq.empty
    metadataStatusResets :+ metadataReviewStatusUpdate
  }
}

case class SelectedStatusData(statusId: String, statusReason: Option[String])
