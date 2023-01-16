package controllers

import auth.UnprotectedPageController
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

@Singleton
class HelpController @Inject() (securityComponents: SecurityComponents) extends UnprotectedPageController(securityComponents) with I18nSupport {
  def help(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.help(request.isLoggedIn, request.isJudgmentUser))
  }

  def judgmentHelp(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.judgment.judgmentHelp(request.isLoggedIn))
  }
}
