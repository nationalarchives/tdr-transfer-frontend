package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses.{DraftMetadataType, InProgressValue}
import services.{ConsignmentService, ConsignmentStatusService}

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
      consignmentRef <- consignmentService.getConsignmentRef(consignmentId)
    } yield Ok(views.html.draftmetadata.prepareMetadata(consignmentId, consignmentRef))
  }

  def prepareMetadataSubmit(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val token = request.token.bearerAccessToken
    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId)
      draftMetadataStatus = consignmentStatusService.getStatusValues(consignmentStatuses, DraftMetadataType).values.headOption.flatten
      _ <- draftMetadataStatus match {
        case Some(_) => Future.successful(())
        case None    => consignmentStatusService.addConsignmentStatus(consignmentId, DraftMetadataType.id, InProgressValue.value)
      }
    } yield Redirect(routes.DraftMetadataUploadController.draftMetadataUploadPage(consignmentId))
  }
}
