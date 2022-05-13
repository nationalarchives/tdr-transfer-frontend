package controllers

import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test._
import util.FrontEndTestHelper

import scala.concurrent._

class HomeControllerSpec extends FrontEndTestHelper {

  "HomeController GET" should {

    "render the index page from a new instance of controller if a user is logged out" in {
      val controller = new HomeController(getUnauthorisedSecurityComponents)
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("This is a new service – your feedback will help us to improve it. Please")
      contentAsString(home) must include ("href=\"/contact\">get in touch (opens in new tab).</a>")
      contentAsString(home) must include ("The National Archives Transfer Digital Records")
      contentAsString(home) must include ("Use this service to:")
      contentAsString(home) must include ("transfer digital records to The National Archives")
      contentAsString(home) must include ("transfer judgments to The National Archives")
      contentAsString(home) must include ("Start now")
      contentAsString(home) must include ("/contact")
      contentAsString(home) must include ("/cookies")
      contentAsString(home) must include ("All content is available under the")
      contentAsString(home) must include (
        "href=\"https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/\" rel=\"license\">Open Government Licence v3.0"
      )
      contentAsString(home) must include (", except where otherwise stated")
      contentAsString(home) must include ("© Crown copyright")

      contentAsString(home) must not include ("/faq")
      contentAsString(home) must not include ("/help")
      contentAsString(home) must not include ("Sign out")
    }

    "render the index page from a new instance of controller if a user is logged in" in {
      val controller = new HomeController(getAuthorisedSecurityComponents)
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("This is a new service – your feedback will help us to improve it. Please")
      contentAsString(home) must include ("href=\"/contact\">get in touch (opens in new tab).</a>")
      contentAsString(home) must include ("The National Archives Transfer Digital Records")
      contentAsString(home) must include ("Use this service to:")
      contentAsString(home) must include ("transfer digital records to The National Archives")
      contentAsString(home) must include ("transfer judgments to The National Archives")
      contentAsString(home) must include ("Start now")
      contentAsString(home) must include ("/contact")
      contentAsString(home) must include ("/cookies")
      contentAsString(home) must include ("/faq")
      contentAsString(home) must include ("/help")
      contentAsString(home) must include ("Sign out")

      contentAsString(home) must include ("All content is available under the")
      contentAsString(home) must include (
        "href=\"https://www.nationalarchives.gov.uk/doc/open-government-licence/version/3/\" rel=\"license\">Open Government Licence v3.0"
      )
      contentAsString(home) must include (", except where otherwise stated")
      contentAsString(home) must include ("© Crown copyright")
    }

    "render the index page from the application" in {
      val controller = inject[HomeController]
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentAsString(home) must include ("Transfer Digital Records")
      contentAsString(home) must include ("Introduction")
      contentAsString(home) must include ("Start now")
    }
  }
}
