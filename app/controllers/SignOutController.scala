package controllers

import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, Request}

import javax.inject.Inject

class SignOutController @Inject() (cc: ControllerComponents) extends AbstractController(cc) with I18nSupport {

  def signedOut(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.signedOut())
  }
}
