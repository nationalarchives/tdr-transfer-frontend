package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.types.FileFilters
import org.pac4j.play.scala.SecurityComponents
import play.api.cache.AsyncCacheApi
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.ConsignmentService

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

class AdditionalMetadataNavigationController @Inject() (
    val consignmentService: ConsignmentService,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents,
    val cache: AsyncCacheApi
) extends TokenSecurity {

  def getAllFiles(consignmentId: UUID, metadataType: String): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    getCachedFiles(consignmentId, metadataType, request).map(allFiles => {
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

    if (fileIds.nonEmpty) {
      submitAndRedirectToNextPage(metadataType, fileIds, consignmentId)
    } else {
      getCachedFiles(consignmentId, metadataType, request).map(allFiles => {
        BadRequest(views.html.standard.additionalMetadataNavigation(consignmentId, request.token.name, allFiles, metadataType, displayError = true))
      })
    }
  }

  def submitAndRedirectToNextPage(metadataType: String, fileIds: List[UUID], consignmentId: UUID)(implicit request: Request[AnyContent]): Future[Result] = {
    if (metadataType == "closure") {
      val fileFilters = FileFilters(None, Option(fileIds), None, None)
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
      Future(Redirect(routes.AddAdditionalMetadataController.addAdditionalMetadata(List("Description-Description"), consignmentId, metadataType, fileIds)))
    }
  }

  private def getCachedFiles(consignmentId: UUID, metadataType: String, request: Request[AnyContent]): Future[ConsignmentService.File] = {
    cache.getOrElseUpdate(s"$consignmentId-$metadataType-allFiles", 1.hour)(
      consignmentService.getAllConsignmentFiles(consignmentId, request.token.bearerAccessToken)
    )
  }

}
