package controllers

import auth.OidcSecurity
import javax.inject.Inject
import org.pac4j.play.scala.SecurityComponents
import play.api.Configuration
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, Request}

class KeycloakConfigurationController @Inject ()(val controllerComponents: SecurityComponents,
                                                 configuration: Configuration
                                                ) extends OidcSecurity {

  def keycloak(): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    val authUrl = configuration.get[String]("auth.url")
    Ok(Json.obj("auth-server-url" -> s"$authUrl/auth", "resource" -> "tdr-fe", "realm" -> "tdr", "ssl-required" -> "external"))
  }
}
