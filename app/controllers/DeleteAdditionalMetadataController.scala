package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.MetadataProperty.clientSideOriginalFilepath
import graphql.codegen.types.FileFilters
import org.pac4j.play.scala.SecurityComponents
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
) extends TokenSecurity {

  private val closureDeletionMessage = "You are deleting closure metadata for the following files and setting them as open:"
  private val descriptiveDeletionMessage = "You are deleting descriptive metadata for the following files:"

  def confirmDeleteAdditionalMetadata(consignmentId: UUID, metadataType: String, fileIds: List[UUID]): Action[AnyContent] =
    standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      if (fileIds.isEmpty) {
        Future.failed(new IllegalArgumentException("fileIds are empty"))
      } else {
        val filters = Option(FileFilters(None, Option(fileIds), None, None))
        for {
          consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, filters)
          response <-
            if (consignment.files.nonEmpty) {
              val filePaths = consignment.files.flatMap(_.fileMetadata).filter(_.name == clientSideOriginalFilepath).map(_.value)
              val deletionMessage = if (metadataType == "closure") closureDeletionMessage else descriptiveDeletionMessage
              Future(
                Ok(views.html.standard.confirmDeleteAdditionalMetadata(consignmentId, metadataType, fileIds, filePaths, request.token.name, deletionMessage))
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
          propertiesToDelete: Set[String] = if (metadataType == "closure") Set("ClosureType") else displayProperties.map(_.propertyName).toSet
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
