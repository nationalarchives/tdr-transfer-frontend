package configuration

import akka.stream.Materializer
import auth.OidcSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import org.pac4j.core.profile.ProfileManager
import org.pac4j.play.PlayWebContext
import org.pac4j.play.scala.SecurityComponents
import play.api.Logging
import play.api.mvc.{Filter, RequestHeader, Result}

import javax.inject.Inject
import scala.compat.java8.OptionConverters._
import scala.concurrent.{ExecutionContext, Future}

class AccessLoggingFilter @Inject() (implicit
    val mat: Materializer,
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents,
    executionContext: ExecutionContext
) extends OidcSecurity
    with Filter
    with Logging {

  private val ignoredPaths = List("/keycloak.json", "/silent-sso-login", "/assets")

  def apply(nextFilter: RequestHeader => Future[Result])(request: RequestHeader): Future[Result] = {
    if (ignoredPaths.exists(request.uri.startsWith(_))) {
      nextFilter(request)
    } else {
      nextFilter(request).map { result =>
        val webContext = new PlayWebContext(request)
        val profileManager = new ProfileManager(webContext, sessionStore)
        val userId: String = profileManager.getProfile.asScala
          .map(_.getAttribute("access_token").asInstanceOf[BearerAccessToken])
          .flatMap(token => keycloakConfiguration.token(token.getValue))
          .map(_.userId.toString)
          .getOrElse("user-logged-out")

        logger.info(
          s"method=${request.method} uri=${request.uri} userid=$userId status=${result.header.status}"
        )
        result
      }
    }
  }
}
