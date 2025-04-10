package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.util.UUID
import javax.inject.Inject

class PrepareMetadataController @Inject()(
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService
) extends TokenSecurity {

  def prepareMetadata(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignmentRef <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield Ok(views.html.draftmetadata.prepareMetadata(consignmentId, consignmentRef))
  }
}