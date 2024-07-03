package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.MessagingService.MetadataReviewRequestEvent
import services.Statuses.{InProgressValue, MetadataReviewType}
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class RequestMetadataReviewController @Inject() (
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService,
    val keycloakConfiguration: KeycloakConfiguration,
    val applicationConfig: ApplicationConfig,
    val messagingService: MessagingService
) extends TokenSecurity {

  def requestMetadataReviewPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    if (applicationConfig.blockMetadataReview) {
      Future(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
    } else {
      consignmentService
        .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        .map { ref =>
          Ok(views.html.standard.requestMetadataReview(consignmentId, ref, request.token.name, request.token.email))
        }
    }
  }

  def submitMetadataForReview(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      _ <- consignmentStatusService.addConsignmentStatus(consignmentId, MetadataReviewType.id, InProgressValue.value, request.token.bearerAccessToken)
      summary <- consignmentService.getConsignmentConfirmTransfer(consignmentId, request.token.bearerAccessToken)
    } yield {
      messagingService.sendMetadataReviewRequestNotification(
        MetadataReviewRequestEvent(
          transferringBodyName = summary.transferringBodyName,
          consignmentReference = summary.consignmentReference,
          consignmentId = consignmentId.toString,
          userId = request.token.userId.toString,
          userEmail = request.token.email
        )
      )
      Ok(views.html.standard.metadataReviewStatus(consignmentId, summary.consignmentReference, request.token.name))
    }
  }
}
