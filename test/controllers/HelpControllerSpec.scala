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

    "render the metadata quick guide page from a new instance of controller" in {
      val controller = new HelpController(getUnauthorisedSecurityComponents)
      val metadataGuide = controller.metadataQuickGuide().apply(FakeRequest(GET, "/help/metadataquickguide"))
      val pageAsString = contentAsString(metadataGuide)

      status(metadataGuide) mustBe OK
      contentType(metadataGuide) mustBe Some("text/html")

      checkMetadataQuickGuidePage(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = "", consignmentExists = false)
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

  private def checkMetadataQuickGuidePage(pageAsString: String): Unit = {
    pageAsString must include("<title>User Metadata Quick Guide - Transfer Digital Records - GOV.UK</title>")
    pageAsString must include("""<h1 class="govuk-heading-l">Metadata quick guide</h1>""")
    pageAsString must include("Use this guide to help you complete your metadata template correctly")

    pageAsString must include("""<table class="govuk-table">""")
    // Check for table headers
    pageAsString must include("""<th scope="col" class="govuk-table__header">Column&nbsp;title</th>""")
    pageAsString must include("""<th scope="col" class="govuk-table__header">Details</th>""")
    pageAsString must include("""<th scope="col" class="govuk-table__header">Format</th>""")
    pageAsString must include("""<th scope="col" class="govuk-table__header">Requirement</th>""")
    pageAsString must include("""<th scope="col" class="govuk-table__header">Example</th>""")

    // Check a row of the table
    pageAsString must include("""<th scope="row" class="govuk-table__header">closure period</th>""")
    pageAsString must include(
      """<td class="govuk-table__cell govuk-!-width-one-third">Closed record: Provide a list of the number of years closure for each foi exemption code, in corresponding order.</td>"""
    )
    pageAsString must include("""<td class="govuk-table__cell">Semi-colon separated list of numbers between 1 and 150 (with no spaces)</td>""")
    pageAsString must include("""<td class="govuk-table__cell">Mandatory for closed record</td>""")
    pageAsString must include("""<td class="govuk-table__cell">40;80</td>""")

  }
}
