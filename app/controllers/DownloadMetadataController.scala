package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.CsvUtils
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService}

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

class DownloadMetadataController @Inject() (
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService,
    val customMetadataService: CustomMetadataService,
    val keycloakConfiguration: KeycloakConfiguration
) extends TokenSecurity {

  def downloadMetadataPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map(ref => {
        Ok(views.html.standard.downloadMetadata(consignmentId, ref, request.token.name))
      })
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
    } yield {
      val parseFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd[ ]['T']HH:mm:ss[.SSS][.SS][.S]")
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
      val filteredMetadata: List[CustomMetadata] = customMetadata.filter(_.allowExport).sortBy(_.exportOrdinal.getOrElse(Int.MaxValue))
      val header: List[String] = filteredMetadata.map(f => f.fullName.getOrElse(f.name))
      val fileMetadataRows: List[List[String]] = metadata.files.map(file => {
        val groupedMetadata = file.fileMetadata.groupBy(_.name).view.mapValues(_.map(_.value).mkString("|")).toMap
        filteredMetadata.map(customMetadata =>
          groupedMetadata
            .get(customMetadata.name)
            .map(fileMetadataValue => {
              if (filteredMetadata.find(_.name == customMetadata.name).exists(_.dataType == DataType.DateTime)) {
                LocalDateTime.parse(fileMetadataValue, parseFormatter).format(formatter)
              } else {
                fileMetadataValue
              }
            })
            .getOrElse("")
        )
      })
      val csvString = CsvUtils.writeCsv(header :: fileMetadataRows)
      Ok(csvString)
        .as("text/csv")
        .withHeaders("Content-Disposition" -> s"attachment; filename=${metadata.consignmentReference}-metadata.csv")
    }
  }
}
