package controllers

import java.util.UUID

import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout}
import util.FrontEndTestHelper

class TransferConfirmationControllerSpec extends FrontEndTestHelper {

  "TransferConfirmationController GET" should {
    "render the success page if the export was triggered successfully" in {
      checkConfirmationPage(exportTriggered = true, "transferConfirmation.complete")
    }

    "render the error page if the export was not triggered successfully" in {
      checkConfirmationPage(exportTriggered = false, "govuk-error-message")
    }
  }

  private def checkConfirmationPage(exportTriggered: Boolean, textToCheck: String) = {
    val consignmentId = UUID.randomUUID()
    val controller = new TransferConfirmationController(getAuthorisedSecurityComponents, getValidKeycloakConfiguration)
    val transferConfirmationSubmit = controller.transferConfirmation(consignmentId, exportTriggered)
      .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-confirmation?exportTriggered=$exportTriggered").withCSRFToken)
    contentAsString(transferConfirmationSubmit) must include(textToCheck)
  }
}
