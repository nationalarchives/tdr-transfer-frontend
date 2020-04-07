package controllers

import java.util.UUID

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.GraphQLConfiguration
import graphql.codegen.AddConsignment.{addConsignment => ac}
import graphql.codegen.GetSeries.{getSeries => gs}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures._
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import uk.gov.nationalarchives.tdr.GraphQLClient
import util.FrontEndTestHelper

import scala.concurrent.ExecutionContext

class SeriesDetailsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "SeriesDetailsController GET" should {

    "render the correct series details page with an authenticated user" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val uuid = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val data: client.GraphqlData = client.GraphqlData(Some(gs.Data(List(gs.GetSeries(uuid, uuid,Option.empty, Some("code"), Option.empty)))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series").withCSRFToken)

      playStatus(seriesDetailsPage) mustBe OK
      contentType(seriesDetailsPage) mustBe Some("text/html")
      contentAsString(seriesDetailsPage) must include ("seriesDetails.header")
      contentAsString(seriesDetailsPage) must include ("seriesDetails.title")
      contentAsString(seriesDetailsPage) must include ("seriesDetails.chooseSeries")
      contentAsString(seriesDetailsPage) must include ("id=\"series\"")
      contentAsString(seriesDetailsPage) must include (s"""<option value="${uuid.toString}">code</option>""")

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new SeriesDetailsController(getUnauthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))
      redirectLocation(seriesDetailsPage).get must startWith ("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(seriesDetailsPage) mustBe FOUND
    }

    "render the error page if the api returns errors" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))

      playStatus(seriesDetailsPage) mustBe BAD_REQUEST
      contentType(seriesDetailsPage) mustBe Some("text/html")
      //This test is based on the placeholder error page, it will need to be changed when we implement a new error page.
      contentAsString(seriesDetailsPage) must include ("Error")
      contentAsString(seriesDetailsPage) must include ("govuk-error-message")

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "render the error page if the token is invalid" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Body does not match", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getInvalidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))

      val failure = seriesDetailsPage.failed.futureValue

      failure.getMessage should include("Token not provided")
    }

    "create a consignment when a valid form is submitted and the api response is successful" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[ac.Data, ac.Variables]()
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val seriesId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val consignmentResponse: ac.AddConsignment = new ac.AddConsignment(Some(consignmentId), seriesId)
      val data: client.GraphqlData = client.GraphqlData(Some(ac.Data(consignmentResponse)), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesSubmit = controller.seriesSubmit().apply(FakeRequest().withFormUrlEncodedBody(("series", seriesId.toString)).withCSRFToken)
      playStatus(seriesSubmit) mustBe SEE_OTHER
      redirectLocation(seriesSubmit) must be(Some(s"/consignment/$consignmentId/transfer-agreement"))
    }

    "renders an error when a valid form is submitted but there is an error from the api" in {
      val seriesId = UUID.randomUUID()
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesSubmit = controller.seriesSubmit().apply(FakeRequest(POST, "/series").withFormUrlEncodedBody(("series", seriesId.toString)).withCSRFToken)
      playStatus(seriesSubmit) mustBe BAD_REQUEST
    }

    "display errors when an invalid form is submitted" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents, new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesSubmit = controller.seriesSubmit().apply(FakeRequest(POST, "/series").withCSRFToken)
      playStatus(seriesSubmit) mustBe BAD_REQUEST
      contentAsString(seriesSubmit) must include("govuk-error-message")
      contentAsString(seriesSubmit) must include("Error")
    }

    "will send the correct body if it is present on the user" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Body does not match", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql")).willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      controller.seriesDetails().apply(FakeRequest(GET, "/series")).await()

      val expectedJson = "{\"query\":\"query getSeries($body:String!){getSeries(body:$body){seriesid bodyid name code description}}\",\"variables\":{\"body\":\"Body\"}}"
      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")).withRequestBody(equalToJson(expectedJson)))
    }
  }
}