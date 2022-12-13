package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.MetadataProperty.closurePeriod
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType.{Boolean, DateTime}
import graphql.codegen.types.{FileFilters, FileMetadataFilters}
import org.apache.commons.lang3.BooleanUtils.toStringYesNo
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService}

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class AdditionalMetadataSummaryController @Inject() (
    val consignmentService: ConsignmentService,
    val customMetadataService: CustomMetadataService,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents
) extends TokenSecurity {

  def getSelectedSummaryPage(consignmentId: UUID, metadataType: String, fileIds: List[UUID]): Action[AnyContent] =
    standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      val fileMetadataFilters = metadataType match {
        case "closure"     => FileMetadataFilters(Some(true), None)
        case "descriptive" => FileMetadataFilters(None, Some(true))
        case _             => throw new IllegalArgumentException(s"Invalid metadata type: $metadataType")
      }
      val filters = Option(FileFilters(None, Option(fileIds), None, Option(fileMetadataFilters)))
      for {
        consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, filters)
        customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
        response <- consignment.files match {
          case first :: _ =>
            val filePaths = consignment.files.flatMap(_.fileName)
            Future(
              Ok(
                views.html.standard
                  .additionalMetadataSummary(
                    consignmentId,
                    metadataType,
                    fileIds,
                    filePaths,
                    consignment.consignmentReference,
                    request.token.name,
                    getMetadataForView(first.fileMetadata, customMetadata)
                  )
              )
            )
          case Nil => Future.failed(new IllegalStateException(s"Can't find selected files for the consignment $consignmentId"))
        }
      } yield response
    }

  private def getMetadataForView(metaData: List[GetConsignment.Files.FileMetadata], customMetadata: List[CustomMetadata]): List[FileMetadata] = {

    val formatter = new SimpleDateFormat("dd/MM/yyyy")

    metaData
      .filter(_.value.nonEmpty)
      .map(metaData =>
        customMetadata
          .find(_.name == metaData.name)
          .map(customMetadata =>
            FileMetadata(
              customMetadata.fullName.getOrElse(metaData.name),
              customMetadata.dataType match {
                case DateTime => formatter.format(Timestamp.valueOf(metaData.value))
                case Boolean  => toStringYesNo(metaData.value.toBoolean).capitalize
                case _        => metaData.value + (if (customMetadata.name == closurePeriod) " years" else "")
              }
            )
          )
          .getOrElse(metaData)
      )
  }
}

case class Metadata(foiExemptionAsserted: String, closureStartDate: String, foiExemptionCode: String, closurePeriod: String)
