package controllers

import auth.UnprotectedPageController
import configuration.ApplicationConfig
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import uk.gov.nationalarchives.tdr.schema.generated.{Definitions, DisallowedPuids}

import javax.inject.{Inject, Singleton}

@Singleton
class FaqController @Inject() (securityComponents: SecurityComponents, val applicationConfig: ApplicationConfig)
    extends UnprotectedPageController(securityComponents)
    with I18nSupport {
  def faq(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val disallowedPuids = DisallowedPuids.all.filter(_.active)
    Ok(views.html.faq(request.isLoggedIn, request.name, disallowedPuids, Definitions.languages.all, Definitions.foi_codes.all))
  }

  def judgmentFaq(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.judgment.judgmentFaq(request.isLoggedIn, request.name))
  }
}
