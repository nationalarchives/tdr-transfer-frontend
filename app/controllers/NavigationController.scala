package controllers

import auth.UnprotectedPageController
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

import javax.inject.{Inject, Singleton}

@Singleton
class NavigationController @Inject()(securityComponents: SecurityComponents) extends UnprotectedPageController(securityComponents) with I18nSupport {
  def navigation(previouslySelectedIds: String = ""): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.navigation())
  }

  def submit(): Action[AnyContent] = Action { implicit request: Request[AnyContent] => {
      Ok(views.html.navigation())
    }
  }
}
