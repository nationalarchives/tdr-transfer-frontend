package controllers

import configuration.GraphQLConfiguration
import org.scalatest.Matchers._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import util.FrontEndTestHelper

import scala.concurrent.ExecutionContext

class SeriesDetailsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global
  "SeriesDetailsController GET" should {

    "render the series details page with an authenticated user" in {
      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents(), new GraphQLConfiguration(app.configuration), app.configuration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))
      status(seriesDetailsPage) mustBe OK
      contentType(seriesDetailsPage) mustBe Some("text/html")
      contentAsString(seriesDetailsPage) must include ("seriesDetails.header")
      contentAsString(seriesDetailsPage) must include ("seriesDetails.title")
      contentAsString(seriesDetailsPage) must include ("seriesDetails.chooseSeries")
      contentAsString(seriesDetailsPage) must include ("id=\"series\"")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new SeriesDetailsController(getUnauthorisedSecurityComponents(), new GraphQLConfiguration(app.configuration), app.configuration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))
      redirectLocation(seriesDetailsPage) must be(Some("/auth/realms/tdr/protocol/openid-connect/auth"))
      status(seriesDetailsPage) mustBe 303
    }
  }
}
