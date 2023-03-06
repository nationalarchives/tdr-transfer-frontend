package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.MetadataProperty.fileType
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.ConsignmentService

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class AdditionalMetadataNavigationController @Inject() (
    val consignmentService: ConsignmentService,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents
) extends TokenSecurity {

  def getAllFiles(consignmentId: UUID, metadataType: String, expanded: Option[String] = Some("false")): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      for {
        allFiles <- consignmentService.getAllConsignmentFiles(consignmentId, request.token.bearerAccessToken, metadataType)
      } yield {
        val ex = expanded.contains("true")
        Ok(views.html.standard.additionalMetadataNavigation(consignmentId, request.token.name, allFiles, metadataType, expanded = ex))
      }
  }

  def submitFiles(consignmentId: UUID, metadataType: String): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val formData = request.body.asFormUrlEncoded
    val action = formData.flatMap(_.get("action")).map(_.head)
    val fileIds = formData
      .flatMap(_.get("nested-navigation"))
      .getOrElse(Nil)
      .map(UUID.fromString)
      .toList

    if (fileIds.nonEmpty) {
      submitAndRedirectToNextPage(metadataType, fileIds, consignmentId, action)
    } else {
      for {
        allFiles <- consignmentService.getAllConsignmentFiles(consignmentId, request.token.bearerAccessToken, metadataType)
      } yield {
        BadRequest(views.html.standard.additionalMetadataNavigation(consignmentId, request.token.name, allFiles, metadataType, displayError = true))
      }
    }
  }

  def submitAndRedirectToNextPage(metadataType: String, fileIds: List[UUID], consignmentId: UUID, action: Option[String])(implicit request: Request[AnyContent]): Future[Result] = {
    if (action.contains("edit")) {
      if (metadataType == "closure") {
        consignmentService
          .getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, Some(metadataType), Some(fileIds), Some(List(fileType)))
          .map(consignment => {
            val areAllClosed = consignmentService.areAllFilesClosed(consignment)
            if (areAllClosed) {
              Redirect(routes.AddAdditionalMetadataController.addAdditionalMetadata(consignmentId, metadataType, fileIds))
            } else {
              Redirect(routes.AdditionalMetadataClosureStatusController.getClosureStatusPage(consignmentId, metadataType, fileIds))
            }
          })
      } else {
        Future.successful(Redirect(routes.AddAdditionalMetadataController.addAdditionalMetadata(consignmentId, metadataType, fileIds)))
      }
    } else {
      Future.successful(Redirect(routes.AdditionalMetadataSummaryController.getSelectedSummaryPage(consignmentId, metadataType, fileIds, None)))
    }
  }
}
