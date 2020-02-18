package auth

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import org.pac4j.core.profile.{CommonProfile, ProfileManager}
import org.pac4j.play.PlayWebContext
import org.pac4j.play.scala.Security
import play.api.mvc.{AnyContent, Request}

trait TokenSecurity extends Security[CommonProfile] {

  implicit def requestToRequestWithToken(request: Request[AnyContent]): RequestWithToken = {
    val webContext = new PlayWebContext(request, playSessionStore)
    val profileManager = new ProfileManager[CommonProfile](webContext)
    val profile = profileManager.get(true)
    val token = profile.get().getAttribute("access_token").asInstanceOf[BearerAccessToken]
    RequestWithToken(request, token)
  }
}
