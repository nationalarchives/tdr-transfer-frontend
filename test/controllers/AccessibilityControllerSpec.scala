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
    val normalizedPage = pageAsString.replaceAll("\\s+", " ").replaceAll(">\\s+", ">").replaceAll("\\s+</", "</")
    normalizedPage must include("""<h1 class="govuk-heading-l">Accessibility statement</h1>""")
    normalizedPage must include("""<h2 class="govuk-heading-m">How accessible this website is</h2>""")
    normalizedPage must include("""<h2 class="govuk-heading-m">Reporting accessibility problems with this website</h2>""")
    normalizedPage must include("""<h3 class="govuk-heading-s">Feedback and contact information</h3>""")
    normalizedPage must include("""<h2 class="govuk-heading-m">Enforcement procedure</h2>""")
    normalizedPage must include("""<h2 class="govuk-heading-m">Technical information on this website’s accessibility</h2>""")
    normalizedPage must include("""<h2 class="govuk-heading-m">Compliance status</h2>""")
    normalizedPage must include("""<h2 class="govuk-heading-m">What we're doing to improve accessibility</h2>""")
    normalizedPage must include("""<h2 class="govuk-heading-m">Preparation of this accessibility statement</h2>""")
    normalizedPage must include("These issues have since been fixed and rechecked in March 2026.")
    normalizedPage must include("heading levels are not always structured sequentially")
    normalizedPage must include("An independent accessibility recheck carried out by RNIB on 19 March 2026 found that the service met WCAG 2.2 level AA.")
    normalizedPage must include("The recheck covered a representative sample of the main records transfer, metadata and judgments journeys.")
    normalizedPage must include("22 Level A success criteria as passed, with no failures")
    normalizedPage must include("18 Level AA success criteria as passed, with no failures")
    normalizedPage must include("one remaining advisory issue that does not represent a WCAG 2.2 failure")
    normalizedPage must include("The service was originally audited by RNIB on 7 November 2025.")
    normalizedPage must include("It was independently rechecked on 19 March 2026 against WCAG 2.2 level A and level AA success criteria.")
    normalizedPage must include("This statement was prepared on 5 October 2026.")
    normalizedPage must include("<title>Accessibility statement - Transfer Digital Records - GOV.UK</title>")
  }
}
