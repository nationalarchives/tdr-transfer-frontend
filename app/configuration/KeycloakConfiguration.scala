package configuration

import play.api.Configuration
import sttp.client3.{HttpURLConnectionBackend, Identity, SttpBackend}
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class KeycloakConfiguration @Inject() (configuration: Configuration)(implicit val executionContext: ExecutionContext) {
  implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  val authUrl: String = configuration.get[String]("auth.url")
  val secret: String = configuration.get[String]("auth.secret")

  def token(value: String): Option[Token] = {
    implicit val tdrKeycloakDeployment: TdrKeycloakDeployment =
      TdrKeycloakDeployment(authUrl, "tdr", 3600)
    KeycloakUtils().token(value).toOption
  }

  def userDetails(userId: String): Future[KeycloakUtils.UserDetails] = {
    implicit val tdrKeycloakDeployment: TdrKeycloakDeployment =
      TdrKeycloakDeployment(authUrl, "tdr", 3600)
    KeycloakUtils().userDetails(userId, "tdr", secret)
  }
}
