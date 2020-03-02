package auth

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.KeycloakConfiguration
import org.keycloak.representations.AccessToken
import org.pac4j.core.profile.{CommonProfile, ProfileManager}
import org.pac4j.play.PlayWebContext
import org.pac4j.play.scala.Security
import play.api.mvc.{AnyContent, Request}
import uk.gov.nationalarchives.tdr.keycloak.Token

trait TokenSecurity extends Security[CommonProfile] {

  def keycloakConfiguration: KeycloakConfiguration

  implicit def requestToRequestWithToken(request: Request[AnyContent]): RequestWithToken = {
    val webContext = new PlayWebContext(request, playSessionStore)
    val profileManager = new ProfileManager[CommonProfile](webContext)
    val profile = profileManager.get(true)
    val token: BearerAccessToken = profile.get().getAttribute("access_token").asInstanceOf[BearerAccessToken]
    val accessToken: Token = keycloakConfiguration.token(token.getValue)
    RequestWithToken(request, accessToken)
  }
}
