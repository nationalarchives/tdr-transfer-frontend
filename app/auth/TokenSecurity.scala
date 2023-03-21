package auth

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.KeycloakConfiguration
import io.opentelemetry.api.trace.{Span, SpanContext}
import org.pac4j.core.profile.{ProfileManager, UserProfile}
import org.pac4j.play.PlayWebContext
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.ConsignmentService
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.util.{Optional, UUID}
import scala.concurrent.{ExecutionContext, Future}

trait TokenSecurity extends OidcSecurity with I18nSupport {

  def keycloakConfiguration: KeycloakConfiguration

  def consignmentService: ConsignmentService
  implicit val executionContext: ExecutionContext = consignmentService.ec

  val consignmentIdKey = "ConsignmentId"
  val userIdKey = "UserId"


  def getProfile(request: Request[AnyContent]): Optional[UserProfile] = {
    val webContext = new PlayWebContext(request)
    val profileManager = new ProfileManager(webContext, sessionStore)
    profileManager.getProfile
  }

  implicit def requestToRequestWithToken(request: Request[AnyContent]): RequestWithToken = {
    val profile = getProfile(request)
    val token: BearerAccessToken = profile.get().getAttribute("access_token").asInstanceOf[BearerAccessToken]
    val accessToken: Option[Token] = keycloakConfiguration.token(token.getValue)
    RequestWithToken(request, accessToken)
  }

  def judgmentTypeAction(consignmentId: UUID)(action: Request[AnyContent] => Future[Result]): Action[AnyContent] = secureAction.async { request =>
    consignmentService.getConsignmentType(consignmentId, request.token.bearerAccessToken).flatMap(consignmentType => createResult(action, request, consignmentType == "judgment"))
  }

  def judgmentUserAction(action: Request[AnyContent] => Future[Result]): Action[AnyContent] = secureAction.async { request =>
    createResult(action, request, request.token.isJudgmentUser)
  }

  def standardUserAction(action: Request[AnyContent] => Future[Result]): Action[AnyContent] = secureAction.async { request =>
    createResult(action, request, request.token.isStandardUser)
  }

  def standardTypeAction(consignmentId: UUID)(action: Request[AnyContent] => Future[Result]): Action[AnyContent] = secureAction.async { request =>
    val token = request.token
    consignmentService
      .getConsignmentType(consignmentId, token.bearerAccessToken)
      .flatMap(consignmentType => {

        val current = Span.current()
        current.setAttribute(consignmentIdKey, consignmentId.toString)
        current.setAttribute(userIdKey, token.userId.toString)
        createResult(action, request, consignmentType == "standard")
      })
  }

  private def createResult(action: Request[AnyContent] => Future[Result], request: AuthenticatedRequest[AnyContent], isPermitted: Boolean) = {
    if (isPermitted) {
      action(request)
    } else {
      Future.successful(
        Forbidden(
          views.html.forbiddenError(
            request.token.name,
            getProfile(request).isPresent,
            request.token.isJudgmentUser
          )(request2Messages(request), request)
        )
      )
    }
  }
}
