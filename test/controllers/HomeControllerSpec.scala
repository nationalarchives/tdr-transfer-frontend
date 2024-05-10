package controllers

import play.api.test.Helpers._
import play.api.test._
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

class HomeControllerSpec extends FrontEndTestHelper {

  val checkPageForStaticElements = new CheckPageForStaticElements

  "HomeController GET" should {

    "render the index page from a new instance of controller if a user is logged out" in {
      val controller = new HomeController(getUnauthorisedSecurityComponents)
      val home = controller.index().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(home)

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      checkForContentOnHomePage(pageAsString, signedIn = false)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = "", consignmentExists = false)
    }

    "render the index page from a new instance of controller if a user is logged in" in {
      val controller = new HomeController(getAuthorisedSecurityComponents)
      val home = controller.index().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(home)

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")

      checkForContentOnHomePage(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = "", consignmentExists = false)

      pageAsString must include("/faq")
      pageAsString must include("/help")
    }

    "render the index page from the application" in {
      val controller = inject[HomeController]
      val home = controller.index().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(home)

      status(home) mustBe OK
      contentType(home) mustBe Some("text/html")
      checkForContentOnHomePage(pageAsString, signedIn = false)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = "", consignmentExists = false)
    }
  }

  private def checkForContentOnHomePage(pageAsString: String, signedIn: Boolean = true): Unit = {
    pageAsString must include("<title>The National Archives - Transfer Digital Records - GOV.UK</title>")
    pageAsString must include("Transfer Digital Records")
    pageAsString must include("Use this service to:")
    pageAsString must include("transfer digital records to The National Archives")
    pageAsString must include("transfer judgments to The National Archives")
    pageAsString must include("Start now")
  }
}
