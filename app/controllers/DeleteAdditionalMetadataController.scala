package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.MetadataPagesUtils
import controllers.util.MetadataProperty.clientSideOriginalFilepath
import graphql.codegen.types.FileFilters
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class DeleteAdditionalMetadataController @Inject() (
    val consignmentService: ConsignmentService,
    val customMetadataService: CustomMetadataService,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents
) extends TokenSecurity {

  def confirmDeleteAdditionalMetadata(consignmentId: UUID, metadataType: String, fileIds: List[UUID], metadataAndValueSelectedOnStartPage: List[String]): Action[AnyContent] =
    standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      if (fileIds.isEmpty) {
        Future.failed(new IllegalArgumentException("fileIds are empty"))
      } else {
        val filters: Option[FileFilters] = MetadataPagesUtils.getFileFilters(metadataType, fileIds)
        for {
          consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, filters)
          response <-
            if (consignment.files.nonEmpty) {
              val filePaths = consignment.files.flatMap(_.fileMetadata).filter(_.name == clientSideOriginalFilepath).map(_.value)
              Future(
                Ok(views.html.standard.confirmDeleteAdditionalMetadata(consignmentId, metadataType, fileIds, filePaths, request.token.name, metadataAndValueSelectedOnStartPage))
              )
            } else {
              Future.failed(new IllegalStateException(s"Can't find selected files for the consignment $consignmentId"))
            }
        } yield response
      }
    }

  def deleteAdditionalMetadata(consignmentId: UUID, metadataType: String, fileIds: List[UUID], metadataTypeAndValueSelected: List[String]): Action[AnyContent] =
    standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      if (fileIds.isEmpty) {
        Future.failed(new IllegalArgumentException("fileIds are empty"))
      } else {
        for {
          _ <- customMetadataService.deleteMetadata(fileIds, request.token.bearerAccessToken)
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
