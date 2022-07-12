package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class AdditionalMetadataController @Inject () (val consignmentService: ConsignmentService,
                                               val keycloakConfiguration: KeycloakConfiguration,
                                               val controllerComponents: SecurityComponents
                                              ) extends TokenSecurity {

  def start(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      folderDetails <- consignmentService.getConsignmentFolderInfo(consignmentId, request.token.bearerAccessToken)
      response <- folderDetails.parentFolder match {
        case Some(folder) => Future(Ok(views.html.standard.additionalMetadataStart(folder, reference, consignmentId, request.token.name)))
        case None => Future.failed(new IllegalStateException("Parent folder not found"))
      }
    } yield response
  }
}
