package controllers

import org.scalatest.Matchers._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status, _}
import util.FrontEndTestHelper

class RecordsControllerSpec extends FrontEndTestHelper {

  "RecordsController GET" should {

    "render the records page after initial upload is complete and user clicks continue" in {
      val controller = new RecordsController(getAuthorisedSecurityComponents)
      val recordsPage = controller.recordsPage(123).apply(FakeRequest(GET, "/consignment/123/records"))
      status(recordsPage) mustBe OK
      contentType(recordsPage) mustBe Some("text/html")
      contentAsString(recordsPage) must include ("records.header")
      contentAsString(recordsPage) must include ("records.title")
    }
  }

}
