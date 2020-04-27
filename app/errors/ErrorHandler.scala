package errors

import javax.inject.Inject
import play.api.Logging
import play.api.http.HttpErrorHandler
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc.Results._
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future

class ErrorHandler @Inject() (val messagesApi: MessagesApi) extends HttpErrorHandler with I18nSupport with Logging {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = {
    logger.error(s"Client error with status code $statusCode at path '${request.path}' with message: '$message'")

    Future.successful(
      Status(statusCode)("A client error occurred: " + message)
    )
  }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] = {
    logger.error(s"Internal server error at path '${request.path}'", exception)

    val response = exception match {
      case authException: AuthorisationException =>
        Forbidden(views.html.error(s"Not authorised: ${authException.getMessage}")(request2Messages(request)))
      case e =>
        InternalServerError(views.html.error(s"A server error occurred: ${e.getMessage}")(request2Messages(request)))
    }

    Future.successful(response)
  }
}
