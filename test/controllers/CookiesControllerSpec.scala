package controllers

import play.api.test.Helpers._
import play.api.test._
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

class CookiesControllerSpec extends FrontEndTestHelper {

  val checkPageForStaticElements = new CheckPageForStaticElements

  "CookiesController GET" should {

    "render the cookies page from a new instance of controller if a user is logged out" in {
      val controller = new CookiesController(getUnauthorisedSecurityComponents)
      val cookies = controller.cookies().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(cookies)

      status(cookies) mustBe OK
      contentType(cookies) mustBe Some("text/html")
      checkForContentOnCookiesPage(pageAsString, signedIn = false)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = "", consignmentExists = false)
    }

    "render the cookies page from a new instance of controller if a user is logged in" in {
      val controller = new CookiesController(getAuthorisedSecurityComponents)
      val cookies = controller.cookies().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(cookies)

      status(cookies) mustBe OK
      contentType(cookies) mustBe Some("text/html")

      checkForContentOnCookiesPage(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = "standard", consignmentExists = false)
    }
  }

  private def checkForContentOnCookiesPage(pageAsString: String, signedIn: Boolean = true): Unit = {
    pageAsString must include("<title>Cookies - Transfer Digital Records - GOV.UK</title>")
    pageAsString must include("""<h1 class="govuk-heading-l">Cookies</h1>""")
  }
}
