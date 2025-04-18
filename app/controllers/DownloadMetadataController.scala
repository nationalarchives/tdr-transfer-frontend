package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import controllers.util.{ExcelUtils, RedirectUtils}
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import org.pac4j.play.scala.SecurityComponents
import play.api.Logging
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, ConsignmentStatusService}
import uk.gov.nationalarchives.tdr.validation.utils.ConfigUtils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class DownloadMetadataController @Inject() (
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService,
    val keycloakConfiguration: KeycloakConfiguration,
    val applicationConfig: ApplicationConfig
) extends TokenSecurity
    with Logging {

  def downloadMetadataPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      ref <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
    } yield {
      RedirectUtils.redirectIfReviewInProgress(consignmentId, consignmentStatuses)(
        Ok(views.html.standard.downloadMetadata(consignmentId, ref, request.token.name, applicationConfig.blockMetadataReview))
      )
    }
  }

  implicit class MapUtils(metadata: Map[String, FileMetadata]) {
    def value(key: String): String = {
      metadata.get(key).map(_.value).getOrElse("")
    }
  }

  def downloadMetadataFile(consignmentId: UUID): Action[AnyContent] = standardAndTnaUserAction(consignmentId) { implicit request: Request[AnyContent] =>
    if (request.token.isTNAUser) logger.info(s"TNA User: ${request.token.userId} downloaded metadata for consignmentId: $consignmentId")

    val metadataConfiguration = ConfigUtils.loadConfiguration
    val tdrFileHeaderMapper = metadataConfiguration.propertyToOutputMapper("tdrFileHeader")
    val propertyTypeEvaluator = metadataConfiguration.getPropertyType
    val downloadType = "MetadataDownloadTemplate"
    val fileSortColumn = metadataConfiguration.propertyToOutputMapper("tdrDataLoadHeader")("file_path")
    case class DownloadProperty(name: String, order: Int)

    for {
      metadata <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, None, None)
      downloadProperties = metadataConfiguration.downloadProperties(downloadType).map(downloadProperty => DownloadProperty(downloadProperty._1, downloadProperty._2))
      orderedDownloadNames = downloadProperties.sortBy(_.order).map(downloadProperty => downloadProperty.name)
      excelFile = ExcelUtils.createExcelFile(metadata.consignmentReference, metadata, orderedDownloadNames, tdrFileHeaderMapper, propertyTypeEvaluator, fileSortColumn)
    } yield {
      Ok(excelFile)
        .as("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .withHeaders("Content-Disposition" -> s"attachment; filename=${metadata.consignmentReference}-$getCurrentDateTime.xlsx")
    }
  }

  private def getCurrentDateTime = {
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
    dateTimeFormatter.format(LocalDateTime.now())
  }

}
