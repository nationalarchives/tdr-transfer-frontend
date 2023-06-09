package errors

import com.nimbusds.jwt.SignedJWT
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import org.pac4j.core.exception.TechnicalException
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.Pac4jScalaTemplateHelper
import play.api.Logging
import play.api.http.HttpErrorHandler
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}

import java.util.concurrent.CompletionException
import javax.inject.Inject
import scala.concurrent.Future

class ErrorHandler @Inject() (val messagesApi: MessagesApi, implicit val pac4jTemplateHelper: Pac4jScalaTemplateHelper[CommonProfile])
    extends HttpErrorHandler
    with I18nSupport
    with Logging {

  def getName(request: RequestHeader): String = {
    pac4jTemplateHelper
      .getCurrentProfiles(request)
      .headOption
      .map(profile => {
        val token = profile.getAttribute("access_token").asInstanceOf[BearerAccessToken]
        val parsedToken = SignedJWT.parse(token.getValue).getJWTClaimsSet
        parsedToken.getClaim("name").toString
      })
      .getOrElse("")
  }

  def isLoggedIn(request: RequestHeader): Boolean = pac4jTemplateHelper.getCurrentProfiles(request).nonEmpty

  def judgmentUser(request: RequestHeader): Boolean = {
    pac4jTemplateHelper
      .getCurrentProfiles(request)
      .headOption
      .exists(profile => {
        val token = profile.getAttribute("access_token").asInstanceOf[BearerAccessToken]
        val parsedToken = SignedJWT.parse(token.getValue).getJWTClaimsSet
        parsedToken.getBooleanClaim("judgment_user")
      })
  }

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    logger.error(s"Client error with status code $statusCode at path '${request.path}' with message: '$message'")
    val loggedIn = isLoggedIn(request)
    val name = getName(request)
    val isJudgmentUser = judgmentUser(request)
    val response = statusCode match {
      case 401 =>
        Unauthorized(views.html.unauthenticatedError(name, loggedIn, isJudgmentUser)(request2Messages(request), request))
      case 403 =>
        Forbidden(views.html.forbiddenError(name, loggedIn, isJudgmentUser)(request2Messages(request), request))
      case 404 =>
        NotFound(views.html.notFoundError(name, loggedIn, isJudgmentUser)(request2Messages(request), request))
      case _ =>
        Status(statusCode)(views.html.internalServerError(name, loggedIn, isJudgmentUser)(request2Messages(request), request))
    }

    Future.successful(response)
  }

  def redirectUrl(isJudgmentUser: Boolean): String = {
    if (isJudgmentUser) { "/homepage" }
    else { "/view-transfers" }
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    logger.error(s"Internal server error at path '${request.path}'", exception)
    val name = getName(request)
    val loggedIn = isLoggedIn(request)
    val isJudgmentUser = judgmentUser(request)
    val response = exception match {
      case _: AuthorisationException =>
        Forbidden(views.html.forbiddenError(name, loggedIn, isJudgmentUser)(request2Messages(request), request))
      case e: CompletionException if Option(e.getCause).exists(e => e.isInstanceOf[TechnicalException] && e.getMessage == "State cannot be determined") =>
        Redirect(redirectUrl(isJudgmentUser))
      case _ =>
        InternalServerError(views.html.internalServerError(name, loggedIn, isJudgmentUser)(request2Messages(request), request))
    }

    Future.successful(response)
  }
}
