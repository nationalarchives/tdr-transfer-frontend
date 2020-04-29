package errors

import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.{FlatSpec, Matchers}
import play.api.http.Status
import play.api.i18n.DefaultMessagesApi
import play.api.test.FakeRequest

class ErrorHandlerSpec extends FlatSpec with Matchers {

  val errorHandler = new ErrorHandler(new DefaultMessagesApi())

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
