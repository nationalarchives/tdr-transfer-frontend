package controllers

import java.util.UUID

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.GraphQLConfiguration
import graphql.codegen.GetSeries.{getSeries => gs}
import io.circe.Printer
import graphql.codegen.AddConsignment.{addConsignment => ac}
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import util.FrontEndTestHelper
import play.api.test.CSRFTokenHelper._

import scala.concurrent.ExecutionContext

class SeriesDetailsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.stop()
  }

  "SeriesDetailsController GET" should {

    "render the correct series details page with an authenticated user" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Some(gs.Data(List(gs.GetSeries(1L, 1L,Option.empty, Some("code"), Option.empty)))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents(), new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series").withCSRFToken)

      playStatus(seriesDetailsPage) mustBe OK
      contentType(seriesDetailsPage) mustBe Some("text/html")
      contentAsString(seriesDetailsPage) must include ("seriesDetails.header")
      contentAsString(seriesDetailsPage) must include ("seriesDetails.title")
      contentAsString(seriesDetailsPage) must include ("seriesDetails.chooseSeries")
      contentAsString(seriesDetailsPage) must include ("id=\"series\"")
      contentAsString(seriesDetailsPage) must include ("<option value=\"1\">code</option>")

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new SeriesDetailsController(getUnauthorisedSecurityComponents(),
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))
      redirectLocation(seriesDetailsPage) must be(Some("/auth/realms/tdr/protocol/openid-connect/auth"))
      playStatus(seriesDetailsPage) mustBe SEE_OTHER
    }

    "render the error page if the api returns errors" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(client.GraphqlError("Error", Nil, Nil)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents(),
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))

      playStatus(seriesDetailsPage) mustBe OK
      contentType(seriesDetailsPage) mustBe Some("text/html")
      //This test is based on the placeholder error page, it will need to be changed when we implement a new error page.
      contentAsString(seriesDetailsPage) must include ("Error")
      contentAsString(seriesDetailsPage) must include ("govuk-error-message")

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "render the error page if the token is invalid" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(client.GraphqlError("Body does not match", Nil, Nil)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents(),
        new GraphQLConfiguration(app.configuration), getInvalidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))

      playStatus(seriesDetailsPage) mustBe OK
      contentType(seriesDetailsPage) mustBe Some("text/html")
      //This test is based on the placeholder error page, it will need to be changed when we implement a new error page.
      contentAsString(seriesDetailsPage) must include ("Body does not match")
      contentAsString(seriesDetailsPage) must include ("govuk-error-message")

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "create a consignment when a valid form is submitted and the api response is successful" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[ac.Data, ac.Variables]()
      val consignmentId = 1
      val seriesId = 1
      val consignmentResponse: ac.AddConsignment = new ac.AddConsignment(Some(consignmentId), seriesId, UUID.randomUUID())
      val data: client.GraphqlData = client.GraphqlData(Some(ac.Data(consignmentResponse)), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents(), new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesSubmit = controller.seriesSubmit().apply(FakeRequest().withFormUrlEncodedBody(("series", seriesId.toString)).withCSRFToken)
      playStatus(seriesSubmit) mustBe SEE_OTHER
      redirectLocation(seriesSubmit) must be(Some(s"/consignment/$consignmentId/transfer-agreement"))
    }

    "redirect to the error page when a valid form is submitted but there is an error from the api" in {
      val seriesId = 1
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(client.GraphqlError("Error", Nil, Nil)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents(), new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesSubmit = controller.seriesSubmit().apply(FakeRequest(POST, "/series").withFormUrlEncodedBody(("series", seriesId.toString)).withCSRFToken)
      playStatus(seriesSubmit) mustBe SEE_OTHER
      redirectLocation(seriesSubmit) must be(Some(s"/error?message=Error"))
    }

    "display errors when an invalid form is submitted" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(client.GraphqlError("Error", Nil, Nil)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents(), new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesSubmit = controller.seriesSubmit().apply(FakeRequest(POST, "/series").withCSRFToken)
      playStatus(seriesSubmit) mustBe BAD_REQUEST
      contentAsString(seriesSubmit) must include("govuk-error-message")
      contentAsString(seriesSubmit) must include("Error")
    }

    "will send a null body if it is not present on the user" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(client.GraphqlError("Body does not match", Nil, Nil)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents(),
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfigurationWithoutBody)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))

      playStatus(seriesDetailsPage) mustBe OK
      contentType(seriesDetailsPage) mustBe Some("text/html")
      val expectedJson = "{\"query\":\"query getSeries($body:String){getSeries(body:$body){seriesid bodyid name code description}}\",\"variables\":{\"body\":null}}"
      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")).withRequestBody(equalToJson(expectedJson)))
    }

    "will send the correct body if it is present on the user" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(client.GraphqlError("Body does not match", Nil, Nil)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents(),
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))

      playStatus(seriesDetailsPage) mustBe OK
      contentType(seriesDetailsPage) mustBe Some("text/html")
      val expectedJson = "{\"query\":\"query getSeries($body:String){getSeries(body:$body){seriesid bodyid name code description}}\",\"variables\":{\"body\":\"Body\"}}"
      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")).withRequestBody(equalToJson(expectedJson)))
    }

  }
}