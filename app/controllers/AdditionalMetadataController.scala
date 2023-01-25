package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
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
      (closure, descriptive) <- displayPropertiesService
        .getDisplayProperties(consignmentId, request.token.bearerAccessToken, None)
        .map(_.partition(byClosureType))
        .map(m => (m._1.map(_.summary), m._2.map(_.summary)))
    } yield Ok(views.html.standard.additionalMetadataStart(consignment.consignmentReference, consignmentId, request.token.name, closure, descriptive))
  }
}
