package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.MetadataProperty.titleAlternate
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

  def getSelectedSummaryPage(consignmentId: UUID, metadataType: String, fileIds: List[UUID], page: Option[String] = None): Action[AnyContent] =
    standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      for {
        consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, Some(metadataType), Some(fileIds))
        displayProperties <- displayPropertiesService.getDisplayProperties(consignmentId, request.token.bearerAccessToken, Some(metadataType))
        response <- consignment.files match {
          case first :: _ =>
            val filePaths = consignment.files.flatMap(_.fileName)
            val metadataStatusType = if (metadataType == "closure") "ClosureMetadata" else "DescriptiveMetadata"
            val hasMetadata = first.fileStatuses.exists(p => p.statusType == metadataStatusType && p.statusValue != "NotEntered")
            if (page.contains("review")) {
              Future.successful(
                Ok(
                  views.html.standard.additionalMetadataReview(
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
            } else {
              Future.successful(
                Ok(
                  views.html.standard.additionalMetadataView(
                    consignmentId,
                    metadataType,
                    fileIds,
                    filePaths,
                    consignment.consignmentReference,
                    request.token.name,
                    getMetadataForView(first.fileMetadata, displayProperties),
                    hasMetadata
                  )
                )
              )
            }
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
        getDisplayName(displayProperty),
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

  private def getDisplayName(displayProperty: DisplayProperty) = {
    val fieldsWithAlternativeName = List(titleAlternate)
    if (fieldsWithAlternativeName.contains(displayProperty.propertyName)) displayProperty.alternativeName else displayProperty.displayName
  }
}
