package auth

import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import org.pac4j.core.profile.{CommonProfile, ProfileManager}
import org.pac4j.play.PlayWebContext
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.mvc.{AnyContent, Request}

import javax.inject.Inject

class UnprotectedPageController @Inject ()(val controllerComponents: SecurityComponents) extends Security[CommonProfile] {

  implicit class RequestUtils(request: Request[AnyContent]) {
    val webContext = new PlayWebContext(request, playSessionStore)
    val profileManager = new ProfileManager[CommonProfile](webContext)
    def isLoggedIn: Boolean = {
      profileManager.get(true).isPresent
    }

    def name: String = {
      val profile = profileManager.get(true)
      if(profile.isPresent){
        val token: BearerAccessToken = profile.get().getAttribute("access_token").asInstanceOf[BearerAccessToken]
        val parsedToken = SignedJWT.parse(token.getValue).getJWTClaimsSet
        parsedToken.getClaim("name").toString
      } else {
        ""
      }
    }
  }
}
