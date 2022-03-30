package errors

import org.pac4j.core.config.Config
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.Pac4jScalaTemplateHelper
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.{FlatSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.http.Status
import play.api.i18n.DefaultMessagesApi
import play.api.test.FakeRequest

class ErrorHandlerSpec extends FlatSpec with Matchers {

  val sessionStore: SessionStore = mock[SessionStore]
  val pac4jTemplateHelper : Pac4jScalaTemplateHelper[CommonProfile] = new Pac4jScalaTemplateHelper[CommonProfile](sessionStore, Config.INSTANCE)
  val errorHandler = new ErrorHandler(new DefaultMessagesApi(), pac4jTemplateHelper)

  "client error handler" should "return a Default response for any status code not explicitly handled" in {
    val request = FakeRequest()
    val unhandledStatusCode = 499
    val response = errorHandler.onClientError(request, unhandledStatusCode).futureValue

    response.header.status should equal(unhandledStatusCode)
  }

  "client error handler" should "return a 401 Unauthorized response if user is unauthorized" in {

    val request = FakeRequest()
    val response = errorHandler.onClientError(request, 401).futureValue

    response.header.status should equal(Status.UNAUTHORIZED)
  }

  "client error handler" should "return a 403 Forbidden response if access is denied" in {

    val request = FakeRequest()
    val response = errorHandler.onClientError(request, 403).futureValue

    response.header.status should equal(Status.FORBIDDEN)
  }

  "client error handler" should "return a 404 Not Found response if the requested page does not exist" in {

    val request = FakeRequest()
    val response = errorHandler.onClientError(request, 404).futureValue

    response.header.status should equal(Status.NOT_FOUND)
  }

  "server error handler" should "return a 403 Forbidden response if an authorisation exception is thrown" in {
    val request = FakeRequest()
    val exception = new AuthorisationException("some authorisation error")

    val response = errorHandler.onServerError(request, exception).futureValue

    response.header.status should equal(Status.FORBIDDEN)
  }

  "server error handler" should "return a 500 Internal Server Error response if an arbitrary exception is thrown" in {
    val request = FakeRequest()
    val exception = new Exception("some generic exception")

    val response = errorHandler.onServerError(request, exception).futureValue

    response.header.status should equal(Status.INTERNAL_SERVER_ERROR)
  }
}
