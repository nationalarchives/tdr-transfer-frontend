package controllers

import auth.TokenSecurity
import com.github.tototoshi.csv.CSVWriter
import configuration.KeycloakConfiguration
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.types.FileFilters
import org.apache.commons.io.output.ByteArrayOutputStream
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService}

import java.util.UUID
import javax.inject.Inject

class DownloadMetadataController @Inject()(val controllerComponents: SecurityComponents,
                                           val consignmentService: ConsignmentService,
                                           val customMetadataService: CustomMetadataService,
                                           val keycloakConfiguration: KeycloakConfiguration) extends TokenSecurity {

  def downloadMetadataPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken).map(ref => {
        Ok(views.html.standard.downloadMetadata(consignmentId, ref, request.token.name))
      })
  }

  implicit class MapUtils(metadata: Map[String, FileMetadata]) {
    def value(key: String): String = {
      metadata.get(key).map(_.value).getOrElse("")
    }
  }

  def downloadMetadataCsv(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      val fileFilters = FileFilters(Option("File"), None, None)
      for {
        metadata <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, Option(fileFilters))
        customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
      } yield {
        val filePathKey = "ClientSideOriginalFilepath"
        val filteredMetadata = customMetadata.filter(_.allowExport).sortBy(_.exportOrdinal.getOrElse(Int.MaxValue))
        val header: List[String] = "File Name" :: filteredMetadata.map(f => f.fullName.getOrElse(f.name))
        val rows: List[List[String]] = metadata.files.map(file => {
          val groupedMetadata = file.fileMetadata.groupBy(_.name).view.mapValues(_.head).toMap
          groupedMetadata.value(filePathKey) :: filteredMetadata.map(fm => groupedMetadata.value(fm.name))
        })
        val csvString = writeCsv(header :: rows)
        Ok(csvString)
          .as("text/csv")
          .withHeaders("Content-Disposition" -> s"attachment; filename=${metadata.consignmentReference}-metadata.csv")
      }
  }

  private def writeCsv(rows: List[List[String]]): String = {
    val bas = new ByteArrayOutputStream()
    val writer = CSVWriter.open(bas)
    writer.writeAll(rows)
    bas.toByteArray.map(_.toChar).mkString
  }
}
