package controllers

import play.api.test.Helpers._
import play.api.test._
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

class AccessibilityControllerSpec extends FrontEndTestHelper {

  val checkPageForStaticElements = new CheckPageForStaticElements

  "AccessibilityController GET" should {

    "render the standard accessibility statement page from a new instance of controller if a user is logged out" in {
      val controller = new AccessibilityController(getUnauthorisedSecurityComponents)
      val accessibilityStatement = controller.accessibilityStatement().apply(FakeRequest(GET, "/accessibility-statement"))
      val pageAsString = contentAsString(accessibilityStatement)

      status(accessibilityStatement) mustBe OK
      contentType(accessibilityStatement) mustBe Some("text/html")
      checkForContentOnAccessibilityStatementPage(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = "standard", consignmentExists = false)
    }

    "render the standard accessibility statement page from a new instance of controller if a user is logged in" in {
      val controller = new AccessibilityController(getAuthorisedSecurityComponents)
      val accessibilityStatement = controller.accessibilityStatement().apply(FakeRequest(GET, "/accessibility-statement"))
      val pageAsString = contentAsString(accessibilityStatement)

      status(accessibilityStatement) mustBe OK
      contentType(accessibilityStatement) mustBe Some("text/html")

      checkForContentOnAccessibilityStatementPage(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = "standard", consignmentExists = false)
    }
  }

  private def checkForContentOnAccessibilityStatementPage(pageAsString: String): Unit = {
    pageAsString must include("""<h1 class="govuk-heading-l">Accessibility statement</h1>""")
    pageAsString must include("""<h2 class="govuk-heading-m">How accessible this website is</h2>""")
    pageAsString must include("""<h2 class="govuk-heading-m">Feedback and contact information</h2>""")
    pageAsString must include("""<h2 class="govuk-heading-m">Reporting accessibility problems with this website</h2>""")
    pageAsString must include("""<h2 class="govuk-heading-m">Enforcement procedure</h2>""")
    pageAsString must include("""<h2 class="govuk-heading-m">Technical information on this website’s accessibility</h2>""")
    pageAsString must include("""<h2 class="govuk-heading-m">Compliance status</h2>""")
    pageAsString must include("""<h2 class="govuk-heading-m">Non-accessible content</h2>""")
    pageAsString must include("""<h2 class="govuk-heading-m">How this website was tested</h2>""")
    pageAsString must include("""<h2 class="govuk-heading-m">What we are doing to improve accessibility</h2>""")
    pageAsString must include("""<h2 class="govuk-heading-m">Preparation of this accessibility statement</h2>""")
    pageAsString must include("<title>Accessibility statement - Transfer Digital Records - GOV.UK</title>")
  }
}
