package controllers

import auth.UnprotectedPageController
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

@Singleton
class FaqController @Inject() (securityComponents: SecurityComponents) extends UnprotectedPageController(securityComponents) with I18nSupport {
  def faq(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.faq(request.isLoggedIn, request.name))
  }

  def judgmentFaq(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.judgment.judgmentFaq(request.isLoggedIn, request.name))
  }
}
