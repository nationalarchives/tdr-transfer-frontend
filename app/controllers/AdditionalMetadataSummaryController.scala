package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.types.DataType.{Boolean, DateTime}
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, DisplayPropertiesService, DisplayProperty}

import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class AdditionalMetadataSummaryController @Inject() (
    val consignmentService: ConsignmentService,
    val displayPropertiesService: DisplayPropertiesService,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents
) extends TokenSecurity {

  def getSelectedSummaryPage(consignmentId: UUID, metadataType: String, fileIds: List[UUID]): Action[AnyContent] =
    standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      for {
        consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, Some(metadataType), Some(fileIds))
        displayProperties <- displayPropertiesService.getDisplayProperties(consignmentId, request.token.bearerAccessToken, Some(metadataType))
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
                    getMetadataForView(first.fileMetadata, displayProperties)
                  )
              )
            )
          case Nil => Future.failed(new IllegalStateException(s"Can't find selected files for the consignment $consignmentId"))
        }
      } yield response
    }

  private def getMetadataForView(metadata: List[GetConsignment.Files.FileMetadata], displayProperties: List[DisplayProperty]): List[FileMetadata] = {

    val formatter = new SimpleDateFormat("dd/MM/yyyy")
    val groupedMetadata = metadata.filter(_.value.nonEmpty).groupBy(_.name).view.mapValues(_.map(_.value).mkString(", ")).toMap

    val filteredMetadata = displayProperties
      .collect {
        case p if p.active && groupedMetadata.contains(p.propertyName) => (p, groupedMetadata(p.propertyName))
      }
      .sortBy(_._1.ordinal)

    filteredMetadata.map { case (displayProperty, metadataValue) =>
      FileMetadata(
        displayProperty.displayName,
        displayProperty.dataType match {
          case DateTime => formatter.format(Timestamp.valueOf(metadataValue))
          case Boolean =>
            metadataValue.toBoolean match {
              case true => displayProperty.label.split('|').head
              case _    => displayProperty.label.split('|').last
            }
          case _ => s"$metadataValue ${displayProperty.unitType.toLowerCase}"
        }
      )
    }
  }
}

case class Metadata(foiExemptionAsserted: String, closureStartDate: String, foiExemptionCode: String, closurePeriod: String)
