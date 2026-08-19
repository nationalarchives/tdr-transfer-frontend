package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.types.ConsignmentStatusInput
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.MessagingService.MetadataReviewRequestEvent
import services.Statuses.{ExportType, InProgressValue, MetadataReviewType}
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

  private def exportView(consignmentId: UUID) = {
    Redirect(routes.ConfirmTransferController.confirmTransfer(consignmentId))
  }

  private def invalidStateChangeView(consignmentId: UUID) = {
    Redirect(routes.DraftMetadataUploadController.draftMetadataUploadPage(consignmentId))
  }

  def requestMetadataReviewPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val token = request.token.bearerAccessToken
    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
      reference <- consignmentService.getConsignmentRef(consignmentId, token)
      exportStatus = consignmentStatusService.getStatusValues(consignmentStatuses, ExportType).values.headOption.flatten
      metadataReviewStatus = consignmentStatusService.getStatusValues(consignmentStatuses, MetadataReviewType).values.headOption.flatten
      stateChange = TransferState(CommonMetadataReviewType).checkStateChange(CommonInProgressValue, CurrentState(consignmentId, consignmentStatuses))
      metadataReviewInProgress = metadataReviewStatus.contains(InProgressValue.value)
      reviewSubmissionLocked = metadataReviewInProgress && stateChange.isLeft
    } yield {
      exportStatus match {
        case _ if exportStatus.isDefined => exportView(consignmentId)
        case _ if reviewSubmissionLocked => Ok(views.html.standard.requestMetadataReviewInProgress(consignmentId, reference, request.token.name))
        case _ if stateChange.isLeft     => invalidStateChangeView(consignmentId)
        case _                           => Ok(views.html.standard.requestMetadataReview(consignmentId, reference, request.token.name, request.token.email))
      }
    }
  }

  def submitMetadataForReview(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val token = request.token.bearerAccessToken
    val response = for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
      exportStatus = consignmentStatusService.getStatusValues(consignmentStatuses, ExportType).values.headOption.flatten
      stateChange = TransferState(CommonMetadataReviewType).checkStateChange(CommonInProgressValue, CurrentState(consignmentId, consignmentStatuses))
      statusesToValue = consignmentStatusService.getStatusValues(consignmentStatuses, MetadataReviewType).values.headOption.flatten
      metadataReviewInProgress = statusesToValue.contains(InProgressValue.value)
      reviewSubmissionLocked = metadataReviewInProgress && stateChange.isLeft
    } yield {
      exportStatus match {
        case _ if exportStatus.isDefined => Future.successful(exportView(consignmentId))
        case _ if reviewSubmissionLocked => Future.successful(Redirect(routes.RequestMetadataReviewController.requestMetadataReviewPage(consignmentId)))
        case _ if stateChange.isLeft     => Future.successful(invalidStateChangeView(consignmentId))
        case _                           =>
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
}
