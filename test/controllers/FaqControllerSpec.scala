package controllers

import play.api.test.Helpers._
import play.api.test._
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

class FaqControllerSpec extends FrontEndTestHelper {

  val checkPageForStaticElements = new CheckPageForStaticElements

  "FaqController GET" should {

    "render the standard faq page from a new instance of controller if a user is logged out" in {
      // the link is not visible in the footer but you could still visit it if you had the URL
      val controller = new FaqController(getUnauthorisedSecurityComponents)
      val faq = controller.faq().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(faq)

      status(faq) mustBe OK
      contentType(faq) mustBe Some("text/html")
      checkForContentOnFaqPage(pageAsString, signedIn = false)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = "standard", consignmentExists = false)
    }

    "render the standard faq page from a new instance of controller if a user is logged in" in {
      val controller = new FaqController(getAuthorisedSecurityComponents)
      val faq = controller.faq().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(faq)

      status(faq) mustBe OK
      contentType(faq) mustBe Some("text/html")

      checkForContentOnFaqPage(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = "standard", consignmentExists = false)
    }

    "render the judgment faq page from a new instance of controller if a user is logged out" in {
      // the link is not visible in the footer but you could still visit it if you had the URL
      val controller = new FaqController(getUnauthorisedSecurityComponents)
      val judgmentFaq = controller.judgmentFaq().apply(FakeRequest(GET, "/"))
      val userType = "judgment"
      val pageAsString = contentAsString(judgmentFaq)

      status(judgmentFaq) mustBe OK
      contentType(judgmentFaq) mustBe Some("text/html")
      checkForContentOnFaqPage(pageAsString, userType, signedIn = false)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = userType, consignmentExists = false)
    }

    "render the judgment faq page from a new instance of controller if a user is logged in" in {
      val controller = new FaqController(getAuthorisedSecurityComponentsForJudgmentUser)
      val judgmentFaq = controller.judgmentFaq().apply(FakeRequest(GET, "/"))
      val userType = "judgment"
      val pageAsString = contentAsString(judgmentFaq)

      status(judgmentFaq) mustBe OK
      contentType(judgmentFaq) mustBe Some("text/html")

      checkForContentOnFaqPage(pageAsString, userType)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = userType, consignmentExists = false)
    }
  }

  private def checkForContentOnFaqPage(pageAsString: String, userType: String = "standard", signedIn: Boolean = true): Unit = {
    pageAsString must include("""<h1 class="govuk-heading-l">Frequently Asked Questions</h1>""")
    pageAsString must include("""<h2 id="getting-started" class="govuk-heading-m">Getting started</h2>""")
    pageAsString must include("""<h2 id="accessing-tdr-account" class="govuk-heading-m">Accessing my TDR account</h2>""")
    pageAsString must include("""<h2 id="upload-process" class="govuk-heading-m">The upload process</h2>""")
    pageAsString must include("""<h2 id="general" class="govuk-heading-m">General</h2>""")

    if (userType == "standard") {
      pageAsString must include("<title>FAQ</title>")
    } else {
      pageAsString must include("<title>judgmentFAQ</title>")

    }
  }
}
