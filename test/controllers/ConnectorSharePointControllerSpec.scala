package controllers

import configuration.ApplicationConfig
import org.mockito.Mockito.when
import play.api.test.Helpers._
import play.api.test._
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

class ConnectorSharePointControllerSpec extends FrontEndTestHelper {

  val checkPageForStaticElements = new CheckPageForStaticElements

  "ConnectorSharePointController GET" should {

    "render the help page if a user is logged in" in {
      val mockConfig: ApplicationConfig = mock[ApplicationConfig]
      when(mockConfig.blockConnectorSharePointPages).thenReturn(false)
      val controller = new ConnectorSharePointController(getAuthorisedSecurityComponents, mockConfig)
      val help = controller.help().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(help)

      status(help) mustBe OK
      contentType(help) mustBe Some("text/html")
      checkHelpPageContent(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = "standard", consignmentExists = false)
    }

    "render the help page if a user is logged out" in {
      val mockConfig: ApplicationConfig = mock[ApplicationConfig]
      when(mockConfig.blockConnectorSharePointPages).thenReturn(false)
      val controller = new ConnectorSharePointController(getUnauthorisedSecurityComponents, mockConfig)
      val help = controller.help().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(help)

      status(help) mustBe OK
      contentType(help) mustBe Some("text/html")
      checkHelpPageContent(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = "", consignmentExists = false)
    }

    "render not found error page when access to help page blocked" in {
      val mockConfig: ApplicationConfig = mock[ApplicationConfig]
      when(mockConfig.blockConnectorSharePointPages).thenReturn(true)
      val controller = new ConnectorSharePointController(getUnauthorisedSecurityComponents, mockConfig)
      val help = controller.help().apply(FakeRequest(GET, "/"))
      status(help) mustBe NOT_FOUND
      contentType(help) mustBe Some("text/html")
    }

    "render the licence page if a user is logged in" in {
      val mockConfig: ApplicationConfig = mock[ApplicationConfig]
      when(mockConfig.blockConnectorSharePointPages).thenReturn(false)
      val controller = new ConnectorSharePointController(getAuthorisedSecurityComponents, mockConfig)
      val help = controller.licence().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(help)

      status(help) mustBe OK
      contentType(help) mustBe Some("text/html")
      checkLicencePageContent(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = "standard", consignmentExists = false)
    }

    "render the licence page if a user is logged out" in {
      val mockConfig: ApplicationConfig = mock[ApplicationConfig]
      when(mockConfig.blockConnectorSharePointPages).thenReturn(false)
      val controller = new ConnectorSharePointController(getUnauthorisedSecurityComponents, mockConfig)
      val help = controller.licence().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(help)

      status(help) mustBe OK
      contentType(help) mustBe Some("text/html")
      checkLicencePageContent(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = "standard", consignmentExists = false)
    }

    "render not found error page when access to licence page blocked" in {
      val mockConfig: ApplicationConfig = mock[ApplicationConfig]
      when(mockConfig.blockConnectorSharePointPages).thenReturn(true)
      val controller = new ConnectorSharePointController(getUnauthorisedSecurityComponents, mockConfig)
      val help = controller.licence().apply(FakeRequest(GET, "/"))
      status(help) mustBe NOT_FOUND
      contentType(help) mustBe Some("text/html")
    }

    "render the privacy policy page if a user is logged in" in {
      val mockConfig: ApplicationConfig = mock[ApplicationConfig]
      when(mockConfig.blockConnectorSharePointPages).thenReturn(false)
      val controller = new ConnectorSharePointController(getAuthorisedSecurityComponents, mockConfig)
      val help = controller.privacyPolicy().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(help)

      status(help) mustBe OK
      contentType(help) mustBe Some("text/html")
      checkPrivacyPolicyPageContent(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = "standard", consignmentExists = false)
    }

    "render the privacy policy page if a user is logged out" in {
      val mockConfig: ApplicationConfig = mock[ApplicationConfig]
      when(mockConfig.blockConnectorSharePointPages).thenReturn(false)
      val controller = new ConnectorSharePointController(getUnauthorisedSecurityComponents, mockConfig)
      val help = controller.privacyPolicy().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(help)

      status(help) mustBe OK
      contentType(help) mustBe Some("text/html")
      checkPrivacyPolicyPageContent(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = "standard", consignmentExists = false)
    }

    "render not found error page when access to privacy policy page blocked" in {
      val mockConfig: ApplicationConfig = mock[ApplicationConfig]
      when(mockConfig.blockConnectorSharePointPages).thenReturn(true)
      val controller = new ConnectorSharePointController(getUnauthorisedSecurityComponents, mockConfig)
      val help = controller.privacyPolicy().apply(FakeRequest(GET, "/"))
      status(help) mustBe NOT_FOUND
      contentType(help) mustBe Some("text/html")
    }
  }

  private def checkHelpPageContent(pageAsString: String): Unit = {
    pageAsString must include("<title>TDR Connector for SharePoint Help - Transfer Digital Records - GOV.UK</title>")
    pageAsString must include("""<h1 class="govuk-heading-l">TDR Connector for SharePoint Help</h1>""")
  }

  private def checkLicencePageContent(pageAsString: String): Unit = {
    pageAsString must include("<title>TDR Connector for SharePoint Licence - Transfer Digital Records - GOV.UK</title>")
    pageAsString must include("""<h1 class="govuk-heading-l">TDR Connector for SharePoint Licence</h1>""")
  }

  private def checkPrivacyPolicyPageContent(pageAsString: String): Unit = {
    pageAsString must include("<title>TDR Connector for SharePoint Privacy Policy - Transfer Digital Records - GOV.UK</title>")
    pageAsString must include("""<h1 class="govuk-heading-l">TDR Connector for SharePoint Privacy Policy</h1>""")
  }
}
