package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class AdditionalMetadataController @Inject() (
    val consignmentService: ConsignmentService,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents
) extends TokenSecurity {

  def start(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignment <- consignmentService.getConsignmentDetails(consignmentId, request.token.bearerAccessToken)
      response <- (consignment.parentFolder, consignment.parentFolderId) match {
        case (Some(folder), Some(id)) =>
          Future(Ok(views.html.standard.additionalMetadataStart(folder, id, consignment.consignmentReference, consignmentId, request.token.name)))
        case _ => Future.failed(new IllegalStateException("Parent folder not found"))
      }
    } yield response
  }
}
