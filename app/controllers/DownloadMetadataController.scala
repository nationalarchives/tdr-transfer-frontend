package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.{ExcelUtils, RedirectUtils}
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import org.pac4j.play.scala.SecurityComponents
import play.api.Logging
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, ConsignmentStatusService}
import uk.gov.nationalarchives.tdr.schemautils.ConfigUtils
import uk.gov.nationalarchives.tdr.validation.utils.GuidanceUtils

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class DownloadMetadataController @Inject() (
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService,
    val keycloakConfiguration: KeycloakConfiguration
) extends TokenSecurity
    with Logging {

  def downloadMetadataPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      ref <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
    } yield {
      RedirectUtils.redirectIfReviewInProgress(consignmentId, consignmentStatuses)(
        Ok(views.html.standard.downloadMetadata(consignmentId, ref, request.token.name))
      )
    }
  }

  implicit class MapUtils(metadata: Map[String, FileMetadata]) {
    def value(key: String): String = {
      metadata.get(key).map(_.value).getOrElse("")
    }
  }

  def downloadMetadataFile(consignmentId: UUID, downloadType: Option[String]): Action[AnyContent] = standardAndTnaUserAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      if (request.token.isTNAUser) logger.info(s"TNA User: ${request.token.userId} downloaded metadata for consignmentId: $consignmentId")

      val metadataConfiguration = ConfigUtils.loadConfiguration
      val tdrFileHeaderMapper = metadataConfiguration.propertyToOutputMapper("tdrFileHeader")
      val tdrDataLoadHeaderMapper = metadataConfiguration.propertyToOutputMapper("tdrDataLoadHeader")
      val propertyTypeEvaluator = metadataConfiguration.getPropertyType
      val fileSortColumn = metadataConfiguration.propertyToOutputMapper("tdrDataLoadHeader")("file_path")

      for {
        metadata <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, None, None)
        downloadDisplayProperties = metadataConfiguration.downloadFileDisplayProperties(downloadType.getOrElse("MetadataDownloadTemplate")).sortBy(_.columnIndex)
        excelFile = ExcelUtils.createExcelFile(
          metadata.consignmentReference,
          metadata,
          downloadDisplayProperties,
          tdrFileHeaderMapper,
          tdrDataLoadHeaderMapper,
          propertyTypeEvaluator,
          fileSortColumn,
          metadataConfiguration.getDefaultValue,
          GuidanceUtils.loadGuidanceFile.toOption.getOrElse(Seq.empty)
        )
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
