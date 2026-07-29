package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.types.ConsignmentStatusInput
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, ConsignmentStatusService}
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusTypes.DraftMetadataType
import uk.gov.nationalarchives.tdr.common.utils.statuses.StatusValues._

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class PrepareMetadataController @Inject() (
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService
) extends TokenSecurity {

  def prepareMetadata(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignmentRef <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield Ok(views.html.draftmetadata.prepareMetadata(consignmentId, consignmentRef))
  }

  def prepareMetadataSubmit(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val token = request.token.bearerAccessToken
    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
      draftMetadataStatus = consignmentStatusService.getStatusValues(consignmentStatuses, services.Statuses.DraftMetadataType).values.headOption.flatten
      _ <- draftMetadataStatus match {
        case Some(_) => Future.successful(())
        case None    => consignmentStatusService.addConsignmentStatus(consignmentId, DraftMetadataType.id, InProgressValue.value, token)
      }
    } yield Redirect(routes.DraftMetadataUploadController.draftMetadataUploadPage(consignmentId))
  }

  def skipDraftMetadata(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val token = request.token.bearerAccessToken
    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
      draftMetadataStatus = consignmentStatusService.getStatusValues(consignmentStatuses, services.Statuses.DraftMetadataType).values.headOption.flatten
      _ <- draftMetadataStatus match {
        case Some(_) => consignmentStatusService.updateConsignmentStatus(ConsignmentStatusInput(consignmentId, DraftMetadataType.id, Some(SkippedValue.value), None, None), token)
        case None    => consignmentStatusService.addConsignmentStatus(consignmentId, DraftMetadataType.id, SkippedValue.value, token)
      }
    } yield Redirect(routes.DownloadMetadataController.downloadMetadataPage(consignmentId))
  }
}
