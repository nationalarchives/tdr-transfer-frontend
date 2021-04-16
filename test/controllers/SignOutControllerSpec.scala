package controllers

import play.api.test.FakeRequest
import util.FrontEndTestHelper
import play.api.test.Helpers._


class SignOutControllerSpec extends FrontEndTestHelper {

  "SignOutController" should {
    "render the sign out page" in {
      val controller = new SignOutController(stubControllerComponents())
      val signOutPage = controller.signedOut()(FakeRequest(GET, "/sign-out"))

      status(signOutPage) mustBe OK
      contentType(signOutPage) mustBe Some("text/html")
      contentAsString(signOutPage) must include ("signOut.title")
      contentAsString(signOutPage) must include ("signOut.signInLink")
    }
  }
}
