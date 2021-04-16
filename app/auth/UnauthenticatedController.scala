package auth

import org.pac4j.core.profile.{CommonProfile, ProfileManager}
import org.pac4j.play.PlayWebContext
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.mvc.{AnyContent, Request}

import javax.inject.Inject

class UnauthenticatedController @Inject ()(val controllerComponents: SecurityComponents) extends Security[CommonProfile] {

  implicit class RequestUtils(request: Request[AnyContent]) {
    def isLoggedIn = {
      val webContext = new PlayWebContext(request, playSessionStore)
      val profileManager = new ProfileManager[CommonProfile](webContext)
      profileManager.get(true).isPresent
    }
  }

}
