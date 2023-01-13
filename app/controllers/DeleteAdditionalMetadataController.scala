package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.MetadataProperty.clientSideOriginalFilepath
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService, DisplayPropertiesService}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class DeleteAdditionalMetadataController @Inject() (
    val consignmentService: ConsignmentService,
    val customMetadataService: CustomMetadataService,
    val displayPropertiesService: DisplayPropertiesService,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents
) extends TokenSecurity
    with I18nSupport {

  def confirmDeleteAdditionalMetadata(consignmentId: UUID, metadataType: String, fileIds: List[UUID]): Action[AnyContent] =
    standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      if (fileIds.isEmpty) {
        Future.failed(new IllegalArgumentException("fileIds are empty"))
      } else {
        for {
          consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, None, Some(fileIds), Some(List(clientSideOriginalFilepath)))
          response <-
            if (consignment.files.nonEmpty) {
              val filePaths = consignment.files.flatMap(_.fileMetadata).filter(_.name == clientSideOriginalFilepath).map(_.value)
              Future(
                Ok(views.html.standard.confirmDeleteAdditionalMetadata(consignmentId, metadataType, fileIds, filePaths, request.token.name))
              )
            } else {
              Future.failed(new IllegalStateException(s"Can't find selected files for the consignment $consignmentId"))
            }
        } yield response
      }
    }

  def deleteAdditionalMetadata(consignmentId: UUID, metadataType: String, fileIds: List[UUID]): Action[AnyContent] =
    standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      if (fileIds.isEmpty) {
        Future.failed(new IllegalArgumentException("fileIds are empty"))
      } else {
        for {
          displayProperties <- displayPropertiesService.getDisplayProperties(consignmentId, request.token.bearerAccessToken, metadataType)
          propertiesToDelete: Set[String] = displayProperties.map(_.propertyName).toSet
          _ <- customMetadataService.deleteMetadata(fileIds, request.token.bearerAccessToken, propertiesToDelete)
          response <-
            Future(
              Redirect(
                routes.AdditionalMetadataNavigationController
                  .getAllFiles(consignmentId, metadataType)
              )
            )
        } yield response
      }
    }
}
