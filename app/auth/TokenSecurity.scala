package auth

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.KeycloakConfiguration
import io.opentelemetry.api.trace.Span
import org.pac4j.core.profile.UserProfile
import org.pac4j.oidc.profile.OidcProfile
import org.pac4j.play.PlayWebContext
import org.pac4j.play.context.PlayFrameworkParameters
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
    val parameters = new PlayFrameworkParameters(request)
    val webContext = controllerComponents.config.getWebContextFactory.newContext(parameters).asInstanceOf[PlayWebContext]
    val sessionStore = config.getSessionStoreFactory.newSessionStore(parameters)
    val profileManager = controllerComponents.config.getProfileManagerFactory.apply(webContext, sessionStore)
    profileManager.getProfile
  }

  implicit def requestToRequestWithToken(request: Request[AnyContent]): RequestWithToken = {
    val profile = getProfile(request).get().asInstanceOf[OidcProfile]
    val token: BearerAccessToken = profile.getAccessToken.asInstanceOf[BearerAccessToken]
    val accessToken: Option[Token] = keycloakConfiguration.token(token.getValue)
    RequestWithToken(request, accessToken)
  }
  
  def judgmentUserAction(action: Request[AnyContent] => Future[Result]): Action[AnyContent] = secureAction.async { request =>
    createResult(action, request, request.token.isJudgmentUser)
  }

  def judgmentUserAndTypeAction(consignmentId: UUID)(action: Request[AnyContent] => Future[Result]): Action[AnyContent] = {
    validatedAction(consignmentId, "judgment", _.isJudgmentUser)(action)
  }

  def standardUserAction(action: Request[AnyContent] => Future[Result]): Action[AnyContent] = secureAction.async { request =>
    createResult(action, request, request.token.isStandardUser)
  }

  def standardUserAndTypeAction(consignmentId: UUID)(action: Request[AnyContent] => Future[Result]): Action[AnyContent] = {
    validatedAction(consignmentId, "standard", _.isStandardUser)(action)
  }

  def tnaUserAction(action: Request[AnyContent] => Future[Result]): Action[AnyContent] = secureAction.async { request =>
    createResult(action, request, request.token.isTNAUser)
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

  private def consignmentTypeAction(
    consignmentId: UUID,
    expectedConsignmentType: String
  )(action: Request[AnyContent] => Future[Result]): Action[AnyContent] =
    validatedAction(consignmentId, expectedConsignmentType)(action)

  private def validatedAction(
    consignmentId: UUID,
    expectedConsignmentType: String,
    isPermitted: Token => Boolean = _ => true
  )(action: Request[AnyContent] => Future[Result]): Action[AnyContent] = secureAction.async {
    request =>
      val token = request.token
      consignmentService
        .getConsignmentType(consignmentId, token.bearerAccessToken)
        .flatMap(consignmentType => {
          // These are custom user annotation traces used in Xray
          val current = Span.current()
          current.setAttribute(consignmentIdKey, consignmentId.toString)
          current.setAttribute(userIdKey, token.userId.toString)
          createResult(action, request, consignmentType == expectedConsignmentType && isPermitted(token))
        })
  }
}
