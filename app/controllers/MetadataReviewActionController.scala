package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.MetadataReviewActionController.consignmentStatusUpdates
import controllers.util.{DropdownField, InputNameAndValue}
import graphql.codegen.types.ConsignmentStatusInput
import org.pac4j.play.scala.SecurityComponents
import play.api.Logging
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{request, _}
import services.MessagingService.MetadataReviewSubmittedEvent
import services.Statuses.{
  ClosureMetadataType,
  CompletedValue,
  CompletedWithIssuesValue,
  DescriptiveMetadataType,
  DraftMetadataType,
  InProgressValue,
  IncompleteValue,
  MetadataReviewType
}
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MetadataReviewActionController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService,
    val messagingService: MessagingService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport
    with Logging {

  private val selectedDecisionForm: Form[SelectedStatusData] = Form(
    mapping(
      "status" -> text.verifying("Select a status", t => t.nonEmpty)
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
        _ <- Future.sequence(
          consignmentStatusUpdates(formData).map { case (statusType, statusValue) =>
            consignmentStatusService.updateConsignmentStatus(
              ConsignmentStatusInput(consignmentId, statusType, Some(statusValue)),
              request.token.bearerAccessToken
            )
          }
        )
      } yield {
        messagingService.sendMetadataReviewSubmittedNotification(
          MetadataReviewSubmittedEvent(
            consignmentRef,
            urlLink = generateUrlLink(request, routes.MetadataReviewStatusController.metadataReviewStatusPage(consignmentId).url),
            userEmail,
            formData.statusId
          )
        )
        Redirect(routes.MetadataReviewController.metadataReviews())
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
    for {
      consignment <- consignmentService.getConsignmentDetailForMetadataReview(consignmentId, request.token.bearerAccessToken)
      userDetails <- keycloakConfiguration.userDetails(consignment.userid.toString)
    } yield {
      status(
        views.html.tna.metadataReviewAction(
          consignmentId,
          consignment,
          userDetails.email,
          createDropDownField(List(InputNameAndValue("Approve", CompletedValue.value), InputNameAndValue("Reject", CompletedWithIssuesValue.value)), form)
        )
      )
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
  def consignmentStatusUpdates(formData: SelectedStatusData): Seq[(String, String)] = {
    val metadataReviewStatusUpdate = (MetadataReviewType.id, formData.statusId)
    val metadataStatusResets =
      if (formData.statusId == CompletedWithIssuesValue.value)
        Seq(
          (DescriptiveMetadataType.id, IncompleteValue.value),
          (ClosureMetadataType.id, IncompleteValue.value),
          (DraftMetadataType.id, InProgressValue.value)
        )
      else Seq.empty
    Seq(metadataReviewStatusUpdate) ++ metadataStatusResets
  }
}

case class SelectedStatusData(statusId: String)
