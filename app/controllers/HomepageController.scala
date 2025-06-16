package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class HomepageController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def judgmentHomepageSubmit(): Action[AnyContent] = judgmentUserAction { implicit request: Request[AnyContent] =>
    consignmentService.createConsignment(None, request.token).map(consignment => Redirect(routes.BeforeUploadingController.beforeUploading(consignment.consignmentid.get)))
  }

  def homepageSubmit(): Action[AnyContent] = standardUserAction { implicit request: Request[AnyContent] =>
    consignmentService.createConsignment(None, request.token).map(consignment => Redirect(routes.SeriesDetailsController.seriesDetails(consignment.consignmentid.get)))
  }

  def homepage(): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    {
      if (request.token.isJudgmentUser) {
        Redirect(routes.HomepageController.judgmentHomepage())
      } else if (request.token.isStandardUser) {
        Ok(views.html.standard.homepage(request.token.name))
      } else if (request.token.isTNAUser) {
        Ok(views.html.tna.homepage(request.token.name))
      } else {
        Ok(views.html.registrationComplete(request.token.name))
      }
    }
  }

  def judgmentHomepage(): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    {
      if (request.token.isJudgmentUser) {
        Ok(views.html.judgment.judgmentHomepage(request.token.name, blockViewTransfers = true))
      } else if (request.token.isStandardUser) {
        Redirect(routes.HomepageController.homepage())
      } else {
        Ok(views.html.registrationComplete(request.token.name))
      }
    }
  }
}
