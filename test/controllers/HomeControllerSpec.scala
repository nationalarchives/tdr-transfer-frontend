package controllers

import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test._
import util.FrontEndTestHelper

import scala.concurrent._

class HomeControllerSpec extends FrontEndTestHelper {

  "HomeController GET" should {

    "render the index page from a new instance of controller" in {
      val controller = new HomeController(getUnauthorisedSecurityComponents)
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      contentAsString(home) must include ("The National Archives Transfer Digital Records")
      contentAsString(home) must include ("Use this service to transfer digital records to The National Archives.")
      contentAsString(home) must include ("Start now")
    }

    "render the index page from the application" in {
      val controller = inject[HomeController]
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentAsString(home) must include ("Transfer Digital Records")
      contentAsString(home) must include ("Welcome")
      contentAsString(home) must include ("Start now")
    }

    "show the sign out button if the user is logged in" in {
      val controller = new HomeController(getAuthorisedSecurityComponents)
      val home = controller.index().apply(FakeRequest(GET, "/"))

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")

      contentAsString(home) must include ("Sign out")
    }
  }
}
