package auth

import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import org.pac4j.core.profile.{CommonProfile, ProfileManager, UserProfile}
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.play.PlayWebContext
import org.pac4j.play.context.PlayFrameworkParameters
import org.pac4j.play.scala.{Pac4jScalaTemplateHelper, Security, SecurityComponents}
import play.api.mvc.{AnyContent, Request}

import javax.inject.Inject

class UnprotectedPageController @Inject() (val controllerComponents: SecurityComponents) extends Security[CommonProfile] {

  private def getProfile(request: Request[AnyContent]): ProfileManager = {
    val parameters = new PlayFrameworkParameters(request)
    val sessionStore = config.getSessionStoreFactory.newSessionStore(parameters)
    val webContext = new PlayWebContext(request)
    new ProfileManager(webContext, sessionStore)
  }

  implicit class RequestUtils(request: Request[AnyContent]) {
    def isLoggedIn: Boolean = {
      val profileManager = getProfile(request)
      profileManager.getProfile.isPresent
    }

    def name: String = {
      val profileManager = getProfile(request)
      val profile = profileManager.getProfile
      if (profile.isPresent) {
        val token: BearerAccessToken = profile.get().asInstanceOf[OidcProfile].getAccessToken.asInstanceOf[BearerAccessToken]
        val parsedToken = SignedJWT.parse(token.getValue).getJWTClaimsSet
        parsedToken.getClaim("name").toString
      } else {
        ""
      }
    }

    def isJudgmentUser: Boolean = {
      val profileManager = getProfile(request)
      val profile = profileManager.getProfile
      if (profile.isPresent) {
        val token: BearerAccessToken = profile.get().asInstanceOf[OidcProfile].getAccessToken.asInstanceOf[BearerAccessToken]
        val parsedToken = SignedJWT.parse(token.getValue).getJWTClaimsSet
        parsedToken.getBooleanClaim("judgment_user")
      } else {
        false
      }
    }
  }
}
