package controllers

import auth.TokenSecurity
import cats.implicits.catsSyntaxTuple2Semigroupal
import com.github.tototoshi.csv.CSVWriter
import configuration.KeycloakConfiguration
import controllers.DownloadMetadataController.DownloadableMetadata
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import org.apache.commons.io.output.ByteArrayOutputStream
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

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

  def downloadMetadataCsv(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      for {
        metadata <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken)
        customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
      } yield {
        val sortedMetadata = getSortedMetadata(metadata.files, customMetadata)
        val header = sortedMetadata.head.map(_.name)
        val rows: List[List[String]] = sortedMetadata.map(_.map(_.value))
        val csvString = writeCsv(header :: rows)
        Ok(csvString)
          .as("text/csv")
          .withHeaders("Content-Disposition" -> s"attachment; filename=${metadata.consignmentReference}.csv")
      }
  }

  private def getSortedMetadata(files: List[Files], customMetadata: List[CustomMetadata]): List[List[DownloadableMetadata]] = {
    files.map(file => {
      val metadataMap = file.fileMetadata.groupBy(_.name).view.mapValues(_.head).toMap
      val customMetadataMap = customMetadata.filter(_.allowExport).groupBy(_.name).view.mapValues(_.head).toMap
      val fileName = metadataMap.get("ClientSideOriginalFilepath").map(_.value).getOrElse("")
        DownloadableMetadata("File Name", fileName) :: ((metadataMap, customMetadataMap).tupled flatMap {
        case (name, (fileMetadata, customMetadata)) =>
          DownloadableMetadata(customMetadata.fullName.getOrElse(name), fileMetadata.value, customMetadata.exportOrdinal.getOrElse(Int.MaxValue)) :: Nil
        case _ => Nil
      }).toList.sortBy(_.sortOrdinal)
    })
  }

  private def writeCsv(rows: List[List[String]]): String = {
    val bas = new ByteArrayOutputStream()
    val writer = CSVWriter.open(bas)
    writer.writeAll(rows)
    bas.toByteArray.map(_.toChar).mkString
  }
}

object DownloadMetadataController {
  case class DownloadableMetadata(name: String, value: String, sortOrdinal: Int = 0)
}
