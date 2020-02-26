package controllers

import javax.inject.{Inject, Singleton}
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, Request}

import scala.concurrent.ExecutionContext

@Singleton
class ErrorController @Inject()(val cc: ControllerComponents)(implicit val ec: ExecutionContext)  extends AbstractController(cc) with I18nSupport {
  def error(message: String): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.error(""))
  }
}
