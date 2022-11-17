package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class AdditionalMetadataNavigationController @Inject() (
    val consignmentService: ConsignmentService,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents
) extends TokenSecurity {

  def getAllFiles(consignmentId: UUID, metadataType: String): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getAllConsignmentFiles(consignmentId, request.token.bearerAccessToken)
      .map(allFiles => {
        Ok(views.html.standard.additionalMetadataNavigation(consignmentId, request.token.name, allFiles, metadataType))
      })
  }

  def submitFiles(consignmentId: UUID, metadataType: String): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val fileIds = request.body.asFormUrlEncoded
      .getOrElse(Map())
      .keys
      .toList
      .filter(_ != "csrfToken")
      .map(UUID.fromString)
    if (metadataType == "closure") {
      Future(Redirect(routes.AdditionalMetadataClosureStatusController.getClosureStatusPage(consignmentId, fileIds)))
    } else {
      Future(Redirect(routes.AdditionalMetadataSummaryController.getSelectedSummaryPage(consignmentId, fileIds, List(""))))
    }
  }
}
