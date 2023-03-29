package controllers

import auth.UnprotectedPageController
import com.google.inject.Inject
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

class AccessibilityController @Inject() (securityComponents: SecurityComponents) extends UnprotectedPageController(securityComponents) with I18nSupport {

  def accessibilityStatement(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.accessibilityStatement(request.isLoggedIn, request.name, request.isJudgmentUser))
  }
}
