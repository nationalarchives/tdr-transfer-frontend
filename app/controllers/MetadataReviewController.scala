package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import javax.inject.Inject

class MetadataReviewController @Inject() (
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService
) extends TokenSecurity {

  def metadataReviews(): Action[AnyContent] = tnaUserAction { implicit request: Request[AnyContent] =>
    for {
      consignments <- consignmentService.getConsignmentsForReview(request.token.bearerAccessToken)
    } yield {
      Ok(views.html.tna.metadataReviews(consignments))
    }
  }
}
