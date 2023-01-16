package controllers

import javax.inject._
import play.api.mvc._

@Singleton
class KeycloakController @Inject() (cc: ControllerComponents) extends AbstractController(cc) {

  def silentSsoLogin(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.silentSsoLogin())
  }
}
