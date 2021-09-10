package controllers

import org.scalatest.Matchers._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status, _}
import util.FrontEndTestHelper

class DashboardControllerSpec extends FrontEndTestHelper {

  "DashboardController GET" should {

    "render the dashboard page with an authenticated user" in {
      val controller = new DashboardController(getAuthorisedSecurityComponents)
      val dashboardPage = controller.dashboard().apply(FakeRequest(GET, "/dashboard"))
      status(dashboardPage) mustBe OK
      contentType(dashboardPage) mustBe Some("text/html")
      contentAsString(dashboardPage) must include ("Welcome")
      contentAsString(dashboardPage) must include ("Welcome to the Transfer Digital Records service")
      contentAsString(dashboardPage) must include ("Upload your records to start a new transfer")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new DashboardController(getUnauthorisedSecurityComponents)
      val dashboardPage = controller.dashboard().apply(FakeRequest(GET, "/dashboard"))
      redirectLocation(dashboardPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      status(dashboardPage) mustBe FOUND
    }
  }
}
