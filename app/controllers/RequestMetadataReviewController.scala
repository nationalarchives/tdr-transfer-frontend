package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.types.ConsignmentStatusInput
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.MessagingService.MetadataReviewRequestEvent
import services.Statuses.{InProgressValue, MetadataReviewType}
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}

import java.util.UUID
import javax.inject.Inject

class RequestMetadataReviewController @Inject() (
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService,
    val keycloakConfiguration: KeycloakConfiguration,
    val messagingService: MessagingService
) extends TokenSecurity {

  def requestMetadataReviewPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId)
      .map { ref =>
        Ok(views.html.standard.requestMetadataReview(consignmentId, ref, request.token.name, request.token.email))
      }
  }

  def submitMetadataForReview(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val token = request.token.bearerAccessToken
    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId)
      statusesToValue = consignmentStatusService.getStatusValues(consignmentStatuses, MetadataReviewType).values.headOption.flatten
      _ <-
        if (statusesToValue.isEmpty) {
          consignmentStatusService.addConsignmentStatus(consignmentId, MetadataReviewType.id, InProgressValue.value)
        } else {
          consignmentStatusService.updateConsignmentStatus(consignmentId, MetadataReviewType.id, InProgressValue.value)
        }
      summary <- consignmentService.getConsignmentConfirmTransfer(consignmentId)
    } yield {
      messagingService.sendMetadataReviewRequestNotification(
        MetadataReviewRequestEvent(
          transferringBodyName = if (summary.transferringBodyName.isEmpty) {None} else {Option(summary.transferringBodyName)},
          consignmentReference = summary.consignmentReference,
          consignmentId = consignmentId.toString,
          seriesCode = summary.seriesName,
          userId = request.token.userId.toString,
          userEmail = request.token.email
        )
      )

      Redirect(routes.MetadataReviewStatusController.metadataReviewStatusPage(consignmentId))
    }
  }
}
