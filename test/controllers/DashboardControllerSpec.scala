package controllers

import org.scalatest.Matchers._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status, _}
import util.FrontEndTestHelper

import scala.concurrent.ExecutionContext

class DashboardControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "DashboardController GET" should {

    "render the dashboard page with an authenticated user with no user type" in {
      val controller = new DashboardController(
        getAuthorisedSecurityComponents,
        getValidKeycloakConfiguration)
      val dashboardPage = controller.dashboard().apply(FakeRequest(GET, "/dashboard"))
      status(dashboardPage) mustBe OK
      contentType(dashboardPage) mustBe Some("text/html")
      contentAsString(dashboardPage) must include ("Welcome")
      contentAsString(dashboardPage) must include ("Welcome to the Transfer Digital Records service")
      contentAsString(dashboardPage) must include ("Upload your records to start a new transfer")
    }

    "render the dashboard page with an authenticated standard user" in {
      val controller = new DashboardController(
        getAuthorisedSecurityComponents,
        getValidStandardUserKeycloakConfiguration)
      val dashboardPage = controller.dashboard().apply(FakeRequest(GET, "/dashboard"))
      status(dashboardPage) mustBe OK
      contentType(dashboardPage) mustBe Some("text/html")
      contentAsString(dashboardPage) must include ("Welcome")
      contentAsString(dashboardPage) must include ("Welcome to the Transfer Digital Records service")
      contentAsString(dashboardPage) must include ("Upload your records to start a new transfer")
    }

    "render the judgment dashboard page with an authenticated judgment user" in {
      val controller = new DashboardController(
        getAuthorisedSecurityComponents,
        getValidJudgmentUserKeycloakConfiguration)
      val dashboardPage = controller.dashboard().apply(FakeRequest(GET, "/dashboard"))
      status(dashboardPage) mustBe OK
      contentType(dashboardPage) mustBe Some("text/html")
      contentAsString(dashboardPage) must include ("Judgment Transfer Welcome")
      contentAsString(dashboardPage) must include ("Welcome to the Transfer Digital Records service")
      contentAsString(dashboardPage) must include ("Upload your judgment to start a new transfer")
      contentAsString(dashboardPage) must include ("Placeholder page")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new DashboardController(
        getUnauthorisedSecurityComponents,
        getValidKeycloakConfiguration)
      val dashboardPage = controller.dashboard().apply(FakeRequest(GET, "/dashboard"))
      redirectLocation(dashboardPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      status(dashboardPage) mustBe FOUND
    }
  }
}
