package controllers

import play.api.test.Helpers._
import play.api.test._
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

class HelpControllerSpec extends FrontEndTestHelper {

  val checkPageForStaticElements = new CheckPageForStaticElements

  "HelpController GET" should {

    "render the standard help page from a new instance of controller if a user is logged out" in {
      // the link is not visible in the footer but you could still visit it if you had the URL
      val controller = new HelpController(getUnauthorisedSecurityComponents)
      val help = controller.help().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(help)

      status(help) mustBe OK
      contentType(help) mustBe Some("text/html")
      checkForContentOnHelpPage(pageAsString, signedIn = false)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = "", consignmentExists = false)
    }

    "render the standard help page from a new instance of controller if a user is logged in" in {
      val controller = new HelpController(getAuthorisedSecurityComponents)
      val help = controller.help().apply(FakeRequest(GET, "/"))
      val userType = "standard"
      val pageAsString = contentAsString(help)

      status(help) mustBe OK
      contentType(help) mustBe Some("text/html")

      checkForContentOnHelpPage(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = userType, consignmentExists = false)
    }

    "render the judgment help page from a new instance of controller if a user is logged out" in {
      // the link is not visible in the footer but you could still visit it if you had the URL
      val controller = new HelpController(getUnauthorisedSecurityComponents)
      val judgmentHelp = controller.judgmentHelp().apply(FakeRequest(GET, "/"))
      val userType = "judgment"
      val pageAsString = contentAsString(judgmentHelp)

      status(judgmentHelp) mustBe OK
      contentType(judgmentHelp) mustBe Some("text/html")
      checkForContentOnHelpPage(pageAsString, userType, signedIn = false)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = "", consignmentExists = false)
    }

    "render the judgment help page from a new instance of controller if a user is logged in" in {
      val controller = new HelpController(getAuthorisedSecurityComponentsForJudgmentUser)
      val judgmentHelp = controller.judgmentHelp().apply(FakeRequest(GET, "/"))
      val userType = "judgment"
      val pageAsString = contentAsString(judgmentHelp)

      status(judgmentHelp) mustBe OK
      contentType(judgmentHelp) mustBe Some("text/html")

      checkForContentOnHelpPage(pageAsString, userType)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = userType, consignmentExists = false)
    }
  }

  private def checkForContentOnHelpPage(pageAsString: String, userType: String = "standard", signedIn: Boolean = true): Unit = {

    if (userType == "standard") {
      pageAsString must include("<title>User Help Guide - Transfer Digital Records - GOV.UK</title>")
      pageAsString must include("""<h1 class="govuk-heading-l">User Help Guide</h1>""")
      pageAsString must include("""<h2 class="govuk-heading-m">This user help guide includes:</h2>""")
    } else {
      pageAsString must include("<title>Transferring Judgments to The National Archives - Transfer Digital Records - GOV.UK</title>")
      pageAsString must include(
        """<h1 class="govuk-heading-l" id="transferring-judgments">Transferring Judgments to The National Archives</h1>"""
      )
      pageAsString must include(
        """<h2 class="govuk-heading-m" id="step-by-step-guide">A step-by-step guide to using Transfer Digital Records (TDR)</h2>"""
      )
    }
  }
}
