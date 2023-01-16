package controllers

import auth.OidcSecurity
import javax.inject.Inject
import org.pac4j.play.scala.SecurityComponents
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request}

class KeycloakConfigurationController @Inject() (val controllerComponents: SecurityComponents, configuration: Configuration) extends OidcSecurity {

  /** The route for this endpoint is /keycloak.json This is the standard place which the keycloak javascript library looks for the configuration.
    */
  def keycloak: Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    val authUrl = configuration.get[String]("auth.url")
    Ok(Json.obj("auth-server-url" -> s"$authUrl", "resource" -> "tdr-fe", "realm" -> "tdr", "ssl-required" -> "external"))
  }
}
