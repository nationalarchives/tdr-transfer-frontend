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
class HomepageController @Inject()(val controllerComponents: SecurityComponents,
                                   val keycloakConfiguration: KeycloakConfiguration,
                                   val consignmentService: ConsignmentService)
                                  (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  def judgmentHomepageSubmit(): Action[AnyContent] = secureAction.async {
    implicit request: Request[AnyContent] =>
      consignmentService.createConsignment(None, request.token).map(consignment =>
        Redirect(routes.TransferAgreementController.judgmentTransferAgreement(consignment.consignmentid.get))
      )
  }

  def homepage(): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] => {
      if (request.token.isJudgmentUser) {
        Ok(views.html.judgment.judgmentHomepage())
      } else {
        Ok(views.html.standard.homepage())
      }
    }
  }
}
