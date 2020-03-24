package controllers

import org.mockito.Mockito._
import org.scalatest.Matchers._
import play.api.Configuration
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import util.FrontEndTestHelper
import play.api.test.Helpers._


class KeycloakConfigurationControllerSpec extends FrontEndTestHelper {
  "KeycloakConfigurationController GET" should {
    "return the correct configuration" in {
      val configuration = mock[Configuration]
      val authUrl = "fakeserver"
      doAnswer(_ => authUrl).when(configuration).get[String]("auth.url")
      val controller = new KeycloakConfigurationController(getAuthorisedSecurityComponents, configuration)
      val expectedResult: JsValue = Json.obj(
        "auth-server-url" -> s"$authUrl/auth",
        "resource" -> "tdr-fe",
        "realm" -> "tdr",
        "ssl-required" -> "external")
      val request = controller.keycloak.apply(FakeRequest(GET, "/keycloak.json"))
      contentAsJson(request) shouldBe expectedResult
    }
  }
}
