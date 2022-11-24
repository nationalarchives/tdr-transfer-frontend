package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.types.FileFilters
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
      val fileFilters = FileFilters(None, Option(fileIds), None)
      consignmentService
        .getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, Option(fileFilters))
        .map(consignment => {
          val areAllClosed = consignmentService.areAllFilesClosed(consignment)
          if (areAllClosed) {
            Redirect(routes.AddAdditionalMetadataController.addAdditionalMetadata(List("ClosureType-Closed"), consignmentId, metadataType, fileIds))
          } else {
            Redirect(routes.AdditionalMetadataClosureStatusController.getClosureStatusPage(consignmentId, metadataType, fileIds))
          }
        })
    } else {
      // This is a placeholder; need to remove it once we have Descriptive metadata
      Future(Redirect(routes.AdditionalMetadataSummaryController.getSelectedSummaryPage(consignmentId, metadataType, fileIds, List(""))))
    }
  }
}
