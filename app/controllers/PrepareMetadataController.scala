package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.types.ConsignmentStatusInput
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses.{DraftMetadataType, InProgressValue}
import services.{ConsignmentService, ConsignmentStatusService}

import java.util.UUID
import javax.inject.Inject

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
      draftMetadataStatus = consignmentStatusService.getStatusValues(consignmentStatuses, DraftMetadataType).values.headOption.flatten
      _ = if (draftMetadataStatus.isEmpty) consignmentStatusService.addConsignmentStatus(consignmentId, DraftMetadataType.id, InProgressValue.value, token)
    } yield Redirect(routes.DraftMetadataUploadController.draftMetadataUploadPage(consignmentId))
  }
}
