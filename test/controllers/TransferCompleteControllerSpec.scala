package controllers

import java.util.UUID

import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout}
import util.FrontEndTestHelper

class TransferCompleteControllerSpec extends FrontEndTestHelper {

  "TransferCompleteController GET" should {
    "render the success page if the export was triggered successfully" in {
      val consignmentId = UUID.randomUUID()
      val controller = new TransferCompleteController(getAuthorisedSecurityComponents, getValidKeycloakConfiguration)
      val transferCompleteSubmit = controller.transferComplete(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-complete").withCSRFToken)
      contentAsString(transferCompleteSubmit) must include("transferComplete.title")
    }
  }
}
