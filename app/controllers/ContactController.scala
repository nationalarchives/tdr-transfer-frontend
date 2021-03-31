package controllers

import javax.inject.{Inject, Singleton}
import play.api.i18n.I18nSupport
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, Request}

@Singleton
class ContactController @Inject()(cc: ControllerComponents) extends AbstractController(cc) with I18nSupport  {
  def contact(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.contact())
  }
}

