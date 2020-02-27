package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.GraphQLConfiguration
import graphql.codegen.GetSeries.getSeries._
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import util.FrontEndTestHelper

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
      val client = new GraphQLConfiguration(app.configuration).getClient[Data, Variables]()
      val data: client.GraphqlData = client.GraphqlData(Some(Data(List(GetSeries(1L, 1L, Option.empty, Some("code"), Option.empty)))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))

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
      val controller = new SeriesDetailsController(getUnauthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getValidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))
      redirectLocation(seriesDetailsPage).get must startWith ("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(seriesDetailsPage) mustBe FOUND
    }

    "render the error page if the api returns errors" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[Data, Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(client.GraphqlError("Error", Nil, Nil)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents,
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
      val client = new GraphQLConfiguration(app.configuration).getClient[Data, Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(client.GraphqlError("Body does not match", Nil, Nil)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents,
        new GraphQLConfiguration(app.configuration), getInvalidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails().apply(FakeRequest(GET, "/series"))

      playStatus(seriesDetailsPage) mustBe OK
      contentType(seriesDetailsPage) mustBe Some("text/html")
      //This test is based on the placeholder error page, it will need to be changed when we implement a new error page.
      contentAsString(seriesDetailsPage) must include ("Body does not match")
      contentAsString(seriesDetailsPage) must include ("govuk-error-message")

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }
  }
}
