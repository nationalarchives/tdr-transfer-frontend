package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import controllers.util.ExcelUtils
import controllers.util.MetadataProperty._
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType
import org.pac4j.play.scala.SecurityComponents
import play.api.Logging
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
    val keycloakConfiguration: KeycloakConfiguration,
    val applicationConfig: ApplicationConfig
) extends TokenSecurity
    with Logging {

  def downloadMetadataPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map { ref =>
        Ok(views.html.standard.downloadMetadata(consignmentId, ref, request.token.name, applicationConfig.blockMetadataReview))
      }
  }

  implicit class MapUtils(metadata: Map[String, FileMetadata]) {
    def value(key: String): String = {
      metadata.get(key).map(_.value).getOrElse("")
    }
  }

  def downloadMetadataFile(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    if (request.token.isTNAUser) logger.info(s"TNA User: ${request.token.userId} downloaded metadata for consignmentId: $consignmentId")
    for {
      metadata <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, None, None)
      customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
      displayProperties <- displayPropertiesService.getDisplayProperties(consignmentId, request.token.bearerAccessToken, None, showInactive = true)
    } yield {
      val parseFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm:ss[.SSS][.SS][.S]")

      val columnOrder = Seq(
        clientSideOriginalFilepath,
        fileName,
        clientSideFileLastModifiedDate,
        end_date,
        description,
        former_reference,
        closureType.name,
        closureStartDate,
        closurePeriod,
        foiExemptionCode,
        foiExemptionAsserted,
        titleClosed,
        titleAlternate,
        descriptionClosed,
        descriptionAlternate,
        language,
        filenameTranslated,
        fileUUID
      )

      val nameMap = displayProperties.filter(dp => columnOrder.contains(dp.propertyName)).map(dp => (dp.propertyName, dp.displayName)).toMap
      val filteredMetadata: List[CustomMetadata] = columnOrder.collect(customMetadata.map(cm => cm.name -> cm).toMap).toList
      val header: List[String] = filteredMetadata.map(f => nameMap.getOrElse(f.name, f.name))
      val dataTypes: List[DataType] = filteredMetadata.map(f => f.dataType)

      val fileMetadataRows: List[List[Any]] = metadata.files.sortBy(f => f.fileMetadata.find(_.name == clientSideOriginalFilepath).map(_.value.toUpperCase)).map { file =>
        val groupedMetadata: Map[String, String] = file.fileMetadata.groupBy(_.name).view.mapValues(_.map(_.value).mkString("|")).toMap
        filteredMetadata.map { customMetadata =>
          groupedMetadata
            .get(customMetadata.name)
            .map { fileMetadataValue =>
              customMetadata.dataType match {
                case DataType.DateTime => LocalDateTime.parse(fileMetadataValue, parseFormatter).toLocalDate
                case DataType.Boolean  => if (fileMetadataValue == "true") "Yes" else "No"
                case DataType.Integer  => Integer.valueOf(fileMetadataValue)
                case _                 => fileMetadataValue
              }
            }
            .getOrElse("")
        }
      }

      val excelFile = ExcelUtils.writeExcel(metadata.consignmentReference, header :: fileMetadataRows, dataTypes)
      val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
      val currentDateTime = dateTimeFormatter.format(LocalDateTime.now())
      val excelContentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      Ok(excelFile)
        .as(excelContentType)
        .withHeaders("Content-Disposition" -> s"attachment; filename=${metadata.consignmentReference}-$currentDateTime.xlsx")
    }
  }
}
