package configuration

import javax.inject.Inject
import play.api.Configuration
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils

class KeycloakConfiguration @Inject ()(configuration: Configuration) extends KeycloakUtils(s"${configuration.get[String]("auth.url")}/auth") {}
