package controllers

import play.api.mvc.Result
import play.api.test.Helpers._
import play.api.test._
import util.{CheckPageForStaticElements, FrontEndTestHelper}

import scala.concurrent._

class HomeControllerSpec extends FrontEndTestHelper {

  val checkPageForStaticElements = new CheckPageForStaticElements

  "HomeController GET" should {

    "render the index page from a new instance of controller if a user is logged out" in {
      val controller = new HomeController(getUnauthorisedSecurityComponents)
      val home = controller.index().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(home)

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")

      pageAsString must include ("<title>Introduction</title>")
      pageAsString must include ("This is a new service – your feedback will help us to improve it. Please")
      pageAsString must include ("href=\"/contact\">get in touch (opens in new tab).</a>")
      pageAsString must include ("The National Archives Transfer Digital Records")
      pageAsString must include ("Use this service to:")
      pageAsString must include ("transfer digital records to The National Archives")
      pageAsString must include ("transfer judgments to The National Archives")
      pageAsString must include ("Start now")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false)

      pageAsString must not include ("/faq")
      pageAsString must not include ("/help")
      pageAsString must not include ("Sign out")
    }

    "render the index page from a new instance of controller if a user is logged in" in {
      val controller = new HomeController(getAuthorisedSecurityComponents)
      val home = controller.index().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(home)

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")

      pageAsString must include ("<title>Introduction</title>")
      pageAsString must include ("This is a new service – your feedback will help us to improve it. Please")
      pageAsString must include ("href=\"/contact\">get in touch (opens in new tab).</a>")
      pageAsString must include ("The National Archives Transfer Digital Records")
      pageAsString must include ("Use this service to:")
      pageAsString must include ("transfer digital records to The National Archives")
      pageAsString must include ("transfer judgments to The National Archives")
      pageAsString must include ("Start now")
      pageAsString must include ("/faq")
      pageAsString must include ("/help")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString)
    }

    "render the index page from the application" in {
      val controller = inject[HomeController]
      val home = controller.index().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(home)

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")

      pageAsString must include ("<title>Introduction</title>")
      pageAsString must include ("This is a new service – your feedback will help us to improve it. Please")
      pageAsString must include ("href=\"/contact\">get in touch (opens in new tab).</a>")
      pageAsString must include ("The National Archives Transfer Digital Records")
      pageAsString must include ("Use this service to:")
      pageAsString must include ("transfer digital records to The National Archives")
      pageAsString must include ("transfer judgments to The National Archives")
      pageAsString must include ("Start now")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false)

      pageAsString must not include ("/faq")
      pageAsString must not include ("/help")
      pageAsString must not include ("Sign out")
    }
  }
}
