package controllers

import play.api.test.Helpers._
import play.api.test._
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

class ContactControllerSpec extends FrontEndTestHelper {

  val checkPageForStaticElements = new CheckPageForStaticElements

  "ContactController GET" should {

    "render the contact page from a new instance of controller if a user is logged out" in {
      val controller = new ContactController(getUnauthorisedSecurityComponents)
      val contact = controller.contact().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(contact)

      status(contact) mustBe OK
      contentType(contact) mustBe Some("text/html")
      checkForContentOnContactPage(pageAsString, signedIn = false)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, signedIn = false, userType = "", consignmentExists = false)
    }

    "render the contact page from a new instance of controller if a user is logged in" in {
      val controller = new ContactController(getAuthorisedSecurityComponents)
      val contact = controller.contact().apply(FakeRequest(GET, "/"))
      val pageAsString = contentAsString(contact)

      status(contact) mustBe OK
      contentType(contact) mustBe Some("text/html")

      checkForContentOnContactPage(pageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = "standard", consignmentExists = false)
    }
  }

  private def checkForContentOnContactPage(pageAsString: String, signedIn: Boolean = true): Unit = {
    pageAsString must include("<title>Get in touch - Transfer Digital Records - GOV.UK</title>")
    pageAsString must include("""<h1 class="govuk-heading-l">Get in touch</h1>""")
    pageAsString must include("If you have got feedback, ideas or questions")
    pageAsString must include("""<h1 class="govuk-heading-l">Email</h1>""")
    pageAsString must include(
      """<p class="govuk-body"><a class="govuk-link" href="mailto:nationalArchives.email" data-hsupport="email">nationalArchives.email</a></p>"""
    )
  }
}
