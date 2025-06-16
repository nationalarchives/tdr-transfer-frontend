package controllers

import auth.UnprotectedPageController
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

import javax.inject.{Inject, Singleton}

@Singleton
class ContactController @Inject() (securityComponents: SecurityComponents) extends UnprotectedPageController(securityComponents) with I18nSupport {
  def contact(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.contact(request.isLoggedIn, request.name, request.isJudgmentUser))
  }
}
