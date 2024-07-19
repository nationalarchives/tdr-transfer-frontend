package configuration

import configuration.GraphQLBackend.backend
import play.api.Configuration
import uk.gov.nationalarchives.tdr.GraphQlResponse
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment, Token}

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class KeycloakConfiguration @Inject() (configuration: Configuration)(implicit val executionContext: ExecutionContext) {

  def token(value: String): Option[Token] = {
    implicit val tdrKeycloakDeployment: TdrKeycloakDeployment =
      TdrKeycloakDeployment(s"${configuration.get[String]("auth.url")}", "tdr", 3600)
    KeycloakUtils().token(value).toOption
  }

  def userDetails(userId: String) = {
    implicit val tdrKeycloakDeployment: TdrKeycloakDeployment =
      TdrKeycloakDeployment(s"${configuration.get[String]("auth.url")}", "tdr", 3600)
    KeycloakUtils().userDetails(userId, "tdr", s"${configuration.get[String]("auth.secret")}")
  }
}
