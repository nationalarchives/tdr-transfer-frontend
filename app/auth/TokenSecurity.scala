package auth

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.KeycloakConfiguration
import org.pac4j.core.profile.{CommonProfile, ProfileManager}
import org.pac4j.play.PlayWebContext
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.nationalarchives.tdr.keycloak.Token

import scala.concurrent.Future

trait TokenSecurity extends OidcSecurity with I18nSupport {

  def keycloakConfiguration: KeycloakConfiguration

  implicit def requestToRequestWithToken(request: Request[AnyContent]): RequestWithToken = {
    val webContext = new PlayWebContext(request, playSessionStore)
    val profileManager = new ProfileManager[CommonProfile](webContext)
    val profile = profileManager.get(true)
    val token: BearerAccessToken = profile.get().getAttribute("access_token").asInstanceOf[BearerAccessToken]
    val accessToken: Option[Token] = keycloakConfiguration.token(token.getValue)
    RequestWithToken(request, accessToken)
  }

  def judgmentUserAction(block: Request[AnyContent] => Future[Result]): Action[AnyContent] = secureAction.async { request =>
    println(request.token)
    createResult(block, request, request.token.isJudgmentUser)
  }

  def standardUserAction(block: Request[AnyContent] => Future[Result]): Action[AnyContent] = secureAction.async { request =>
    createResult(block, request, !request.token.isJudgmentUser)
  }

  private def createResult(block: Request[AnyContent] => Future[Result], request: AuthenticatedRequest[AnyContent], isPermitted: Boolean) = {
    if (isPermitted) {
      block(request)
    } else {
      Future.successful(Forbidden(views.html.forbiddenError()(request2Messages(request), request)))
    }
  }
}
