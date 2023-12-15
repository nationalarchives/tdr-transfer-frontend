package configuration

import javax.inject.Inject
import play.api.Configuration
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}

import scala.concurrent.ExecutionContext

class KeycloakConfiguration @Inject() (configuration: Configuration)(implicit val executionContext: ExecutionContext) {
  def token(value: String): Option[Token] = {
    implicit val tdrKeycloakDeployment: TdrKeycloakDeployment = TdrKeycloakDeployment(s"${configuration.get[String]("auth.url")}", "tdr", 18000) //temp increase in user session timeout to 5 hours for TDR-3571
    KeycloakUtils().token(value).toOption
  }
}
