package controllers

import javax.inject._
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Pac4jScalaTemplateHelper, Security}
import org.pac4j.play.scala._
import org.pac4j.play.store.PlaySessionStore
import play.api._
import play.api.mvc._

/**
 * This controller creates an `Action` to handle HTTP requests to the
 * application's home page.
 */
@Singleton
class HomeController @Inject()(val controllerComponents: SecurityComponents) extends Security[CommonProfile] {

  /**
   * Create an Action to render an HTML page.
   *
   * The configuration in the `routes` file means that this method
   * will be called when the application receives a `GET` request with
   * a path of `/`.
   */
  def index() = Secure("OidcClient") { implicit request: Request[AnyContent] =>
    Ok(views.html.index())
  }
}
