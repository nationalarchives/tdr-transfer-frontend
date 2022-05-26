package controllers

import play.api.test.FakeRequest
import util.{CheckPageForStaticElements, FrontEndTestHelper}
import play.api.test.Helpers._


class SignOutControllerSpec extends FrontEndTestHelper {
  val checkPageForStaticElements = new CheckPageForStaticElements

  "SignOutController" should {
    "render the sign out page" in {
      val controller = new SignOutController(stubControllerComponents())
      val signOutPage = controller.signedOut()(FakeRequest(GET, "/signed-out"))
      val signOutPageAsString = contentAsString(signOutPage)

      status(signOutPage) mustBe OK
      contentType(signOutPage) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(signOutPageAsString, signedIn = false)
      signOutPageAsString must include ("<title>Signed out</title>")
      signOutPageAsString must include ("""<h1 class="govuk-heading-l">You have successfully signed out</h1>""")
      signOutPageAsString must include ("""<p class="govuk-body">Thanks for using the Transfer Digital Records service.</p>""")

      contentAsString(signOutPage) must include (
      """      <a href="/homepage" class="govuk-link">
        |        Sign in again
        |      </a>""".stripMargin
      )
    }
  }
}
