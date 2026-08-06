package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.types.ConsignmentStatusInput
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.MessagingService.MetadataReviewRequestEvent
import services.Statuses.{InProgressValue, MetadataReviewType}
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}
import uk.gov.nationalarchives.tdr.common.utils.statecontrol.{CurrentState, TransferState}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusTypes.{MetadataReviewType => CommonMetadataReviewType}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues.{InProgressValue => CommonInProgressValue}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class RequestMetadataReviewController @Inject() (
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService,
    val keycloakConfiguration: KeycloakConfiguration,
    val applicationConfig: configuration.ApplicationConfig,
    val messagingService: MessagingService
) extends TokenSecurity {

  def requestMetadataReviewPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val token = request.token.bearerAccessToken
    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
      reference <- consignmentService.getConsignmentRef(consignmentId, token)
      metadataReviewStatus = consignmentStatusService.getStatusValues(consignmentStatuses, MetadataReviewType).values.headOption.flatten
      stateChange = metadataReviewTransferState.checkStateChange(CommonInProgressValue, CurrentState(consignmentId, consignmentStatuses))
      metadataReviewInProgress = metadataReviewStatus.contains(InProgressValue.value)
      canTransitionToInProgress = stateChange.isRight
    } yield {
      if (metadataReviewInProgress && !canTransitionToInProgress) {
        Ok(views.html.standard.requestMetadataReviewInProgress(consignmentId, reference, request.token.name))
      } else if (metadataReviewInProgress) {
          Ok(views.html.standard.requestMetadataReviewInProgress(consignmentId, reference, request.token.name))
      } else {
        Ok(views.html.standard.requestMetadataReview(consignmentId, reference, request.token.name, request.token.email))
      }
    }
  }

  def submitMetadataForReview(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val token = request.token.bearerAccessToken
    val response = for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
      stateChange = metadataReviewTransferState.checkStateChange(CommonInProgressValue, CurrentState(consignmentId, consignmentStatuses))
      statusesToValue = consignmentStatusService.getStatusValues(consignmentStatuses, MetadataReviewType).values.headOption.flatten
      metadataReviewInProgress = statusesToValue.contains(InProgressValue.value)
    } yield {
      if (metadataReviewInProgress && stateChange.isLeft) {
        Future.successful(Redirect(routes.RequestMetadataReviewController.requestMetadataReviewPage(consignmentId)))
      } else if (metadataReviewInProgress) {
        Future.successful(Redirect(routes.RequestMetadataReviewController.requestMetadataReviewPage(consignmentId)))
      } else {
        for {
          _ <-
            if (statusesToValue.isEmpty) {
              consignmentStatusService.addConsignmentStatus(consignmentId, MetadataReviewType.id, InProgressValue.value, token)
            } else {
              consignmentStatusService.updateConsignmentStatus(ConsignmentStatusInput(consignmentId, MetadataReviewType.id, Some(InProgressValue.value), None, None), token)
            }
          consignmentDetails <- consignmentService.getConsignmentDetailForMetadataReviewRequest(consignmentId, token)
        } yield {
          messagingService.sendMetadataReviewRequestNotification(
            MetadataReviewRequestEvent(
              environment = applicationConfig.frontEndInfo.stage,
              transferringBodyName = consignmentDetails.transferringBodyName,
              consignmentReference = consignmentDetails.consignmentReference,
              consignmentId = consignmentId.toString,
              seriesCode = consignmentDetails.seriesName,
              userId = request.token.userId.toString,
              userEmail = request.token.email,
              closedRecords = consignmentDetails.totalClosedRecords > 0,
              totalRecords = consignmentDetails.totalFiles
            )
          )

          Redirect(routes.MetadataReviewStatusController.metadataReviewStatusPage(consignmentId))
        }
      }
    }
    response.flatten
  }

  private val metadataReviewTransferState: TransferState = new TransferState {
    override val currentStatusType = CommonMetadataReviewType
  }
}
