package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.AdditionalMetadataController._
import graphql.codegen.GetConsignment.getConsignment.GetConsignment
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses.{ClosureMetadataType, CompletedValue, DescriptiveMetadataType, IncompleteValue, NotEnteredValue, StatusType}
import services.{ConsignmentService, DisplayPropertiesService, DisplayProperty}

import java.util.UUID
import javax.inject.Inject

class AdditionalMetadataController @Inject() (
    val consignmentService: ConsignmentService,
    val displayPropertiesService: DisplayPropertiesService,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents
) extends TokenSecurity {

  val byClosureType: DisplayProperty => Boolean = (dp: DisplayProperty) => dp.propertyType == "Closure"

  def start(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignment <- consignmentService.getConsignmentDetails(consignmentId, request.token.bearerAccessToken)
      (closurePropertiesSummaries, descriptivePropertiesSummaries) <- displayPropertiesService
        .getDisplayProperties(consignmentId, request.token.bearerAccessToken, None)
        .map(_.partition(byClosureType))
        .map(m => (m._1.map(_.summary), m._2.map(_.summary)))
    } yield {
      val pageArgs = AdditionalMetadataStartPage(
        consignment.consignmentReference,
        consignmentId,
        request.token.name,
        closurePropertiesSummaries,
        descriptivePropertiesSummaries,
        getValue(consignment.consignmentStatuses, ClosureMetadataType),
        getValue(consignment.consignmentStatuses, DescriptiveMetadataType)
      )
      Ok(views.html.standard.additionalMetadataStart(pageArgs))
    }
  }

  def getValue(statuses: List[GetConsignment.ConsignmentStatuses], statusType: StatusType): MetadataProgress = {
    val notEntered = MetadataProgress("NOT ENTERED", "grey")
    statuses.find(_.statusType == statusType.id).map(_.value).map {
      case NotEnteredValue.value => notEntered
      case CompletedValue.value  => MetadataProgress("ENTERED", "blue")
      case IncompleteValue.value => MetadataProgress("INCOMPLETE", "red")
      case _                     => notEntered
    } getOrElse notEntered
  }
}
object AdditionalMetadataController {
  case class MetadataProgress(value: String, colour: String)
  case class AdditionalMetadataStartPage(
      consignmentRef: String,
      consignmentId: UUID,
      name: String,
      closurePropertiesSummaries: List[String],
      descriptivePropertiesSummaries: List[String],
      closureStatus: MetadataProgress,
      descriptiveStatus: MetadataProgress
  )
}
