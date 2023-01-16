package controllers

import auth.UnprotectedPageController
import org.pac4j.play.scala.SecurityComponents

import javax.inject._
import play.api.i18n.I18nSupport
import play.api.mvc._

/** This controller creates an `Action` to handle HTTP requests to the application's home page.
  */

@Singleton
class HomeController @Inject() (securityComponents: SecurityComponents) extends UnprotectedPageController(securityComponents) with I18nSupport {

  def index(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.index(request.isLoggedIn, request.name, request.isJudgmentUser))
  }
}
