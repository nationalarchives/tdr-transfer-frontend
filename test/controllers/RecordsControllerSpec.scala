package controllers

import configuration.GraphQLConfiguration
import org.scalatest.Matchers._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import util.FrontEndTestHelper

import scala.concurrent.ExecutionContext

class RecordsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "RecordsController GET" should {

    "render the records page after initial upload is complete and user clicks continue" in {
      val controller = new RecordsController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val recordsPage = controller.recordsPage(123).apply(FakeRequest(GET, "/consignment/123/records"))
      playStatus(recordsPage) mustBe OK
      contentType(recordsPage) mustBe Some("text/html")
      contentAsString(recordsPage) must include ("records.header")
      contentAsString(recordsPage) must include ("records.title")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new RecordsController(getUnauthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val recordsPage = controller.recordsPage(123).apply(FakeRequest(GET, "/consignment/123/records"))
      redirectLocation(recordsPage).get must startWith ("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(recordsPage) mustBe FOUND
    }
  }

}
