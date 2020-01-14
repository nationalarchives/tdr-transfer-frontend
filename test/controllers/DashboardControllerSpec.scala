package controllers

import org.scalatest.Matchers._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status, _}
import util.FrontEndTestHelper

class DashboardControllerSpec extends FrontEndTestHelper {

  "DashboardController GET" should {

    "render the dashboard page with an authenticated user" in {
      val controller = new DashboardController(getAuthorisedSecurityComponents())
      val home = controller.dashboard().apply(FakeRequest(GET, "/"))
      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("dashboard.header")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new DashboardController(getUnauthorisedSecurityComponents())
      val home = controller.dashboard().apply(FakeRequest(GET, "/"))
      redirectLocation(home).get should include ("/auth/realms/tdr/protocol/openid-connect/auth")
      status(home) mustBe 303

    }
  }
}
