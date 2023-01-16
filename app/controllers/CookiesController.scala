package controllers

import auth.UnprotectedPageController
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

@Singleton
class CookiesController @Inject() (securityComponents: SecurityComponents) extends UnprotectedPageController(securityComponents) with I18nSupport {
  def cookies(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.cookies(request.isLoggedIn, request.name, request.isJudgmentUser))
  }
}
