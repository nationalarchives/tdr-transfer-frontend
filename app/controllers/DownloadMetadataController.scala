package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.CsvUtils
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService, DisplayPropertiesService}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class DownloadMetadataController @Inject() (
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService,
    val customMetadataService: CustomMetadataService,
    val displayPropertiesService: DisplayPropertiesService,
    val keycloakConfiguration: KeycloakConfiguration
) extends TokenSecurity {

  def downloadMetadataPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map { ref =>
        Ok(views.html.standard.downloadMetadata(consignmentId, ref, request.token.name))
      }
  }

  implicit class MapUtils(metadata: Map[String, FileMetadata]) {
    def value(key: String): String = {
      metadata.get(key).map(_.value).getOrElse("")
    }
  }

  def downloadMetadataCsv(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      metadata <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, None, None)
      customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
      displayProperties <- displayPropertiesService.getDisplayProperties(consignmentId, request.token.bearerAccessToken, None, showInactive = true)
    } yield {
      val parseFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm:ss[.SSS][.SS][.S]")
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
      val nameMap = displayProperties.filter(dp => dp.active || dp.propertyName == "Filename").map(dp => (dp.propertyName, dp.displayName)).toMap
      val filteredMetadata: List[CustomMetadata] = customMetadata.filter(cm => nameMap.keySet.contains(cm.name) && cm.allowExport).sortBy(_.exportOrdinal.getOrElse(Int.MaxValue))
      val header: List[String] = filteredMetadata.map(f => nameMap.getOrElse(f.name, f.name))
      val fileMetadataRows: List[List[String]] = metadata.files.map { file =>
        val groupedMetadata: Map[String, String] = file.fileMetadata.groupBy(_.name).view.mapValues(_.map(_.value).mkString("|")).toMap
        filteredMetadata.map { customMetadata =>
          groupedMetadata
            .get(customMetadata.name)
            .map { fileMetadataValue =>
              customMetadata.dataType match {
                case DataType.DateTime => LocalDateTime.parse(fileMetadataValue, parseFormatter).format(formatter)
                case DataType.Boolean  => if (fileMetadataValue == "true") "Yes" else "No"
                case _                 => fileMetadataValue
              }
            }
            .getOrElse("")
        }
      }
      val csvString = CsvUtils.writeCsv(header :: fileMetadataRows)
      val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
      val currentDateTime = dateTimeFormatter.format(LocalDateTime.now())
      Ok(csvString)
        .as("text/csv")
        .withHeaders("Content-Disposition" -> s"attachment; filename=${metadata.consignmentReference}-$currentDateTime.csv")
    }
  }
}
