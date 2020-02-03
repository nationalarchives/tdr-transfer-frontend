package controllers

import org.scalatest.Matchers._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import util.FrontEndTestHelper

class SeriesDetailsControllerSpec extends FrontEndTestHelper {
  "SeriesDetailsController GET" should {

    "render the series details page with an authenticated user" in {
      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents())
      val home = controller.seriesDetails().apply(FakeRequest(GET, "/"))
      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("seriesDetails.header")
      contentAsString(home) must include ("seriesDetails.title")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new SeriesDetailsController(getUnauthorisedSecurityComponents())
      val home = controller.seriesDetails().apply(FakeRequest(GET, "/"))
      redirectLocation(home).get should include ("/auth/realms/tdr/protocol/openid-connect/auth")
      status(home) mustBe 303
    }
  }
}
