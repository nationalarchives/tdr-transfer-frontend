package controllers

import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, redirectLocation, status => playStatus, _}
import util.FrontEndTestHelper

import scala.concurrent.ExecutionContext

class TransferAgreementControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "TransferAgreementController GET" should {

    "render the transfer agreement page with an authenticated user" in {

      val controller = new TransferAgreementController(getAuthorisedSecurityComponents)
      val transferAgreementPage = controller.transferAgreement(123).apply(FakeRequest(GET, "/consignment/123/transfer-agreement"))

      playStatus(transferAgreementPage) mustBe OK
      contentType(transferAgreementPage) mustBe Some("text/html")
      contentAsString(transferAgreementPage) must include("transferAgreement.header")
      contentAsString(transferAgreementPage) must include("transferAgreement.title")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new TransferAgreementController(getUnauthorisedSecurityComponents)
      val transferAgreementPage = controller.transferAgreement(123).apply(FakeRequest(GET, "/consignment/123/transfer-agreement"))
      redirectLocation(transferAgreementPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(transferAgreementPage) mustBe FOUND
    }
  }
}
