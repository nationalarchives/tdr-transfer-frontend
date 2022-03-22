package controllers

import java.util.UUID
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import errors.GraphQlException
import graphql.codegen.AddConsignment.{addConsignment => ac}
import graphql.codegen.GetSeries.{getSeries => gs}
import graphql.codegen.UpdateConsignmentSeriesId.updateConsignmentSeriesId.{Data, Variables}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures._
import play.api.Configuration
import play.api.Play.materializer
import play.api.libs.ws.WSClient
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import sangria.ast.Document
import services.{ConsignmentService, SeriesService}
import sttp.client.{NothingT, SttpBackend}
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}
import util.FrontEndTestHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class SeriesDetailsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId = UUID.randomUUID()
  val seriesId = UUID.fromString("1ebba1f2-5cd9-4379-a26e-f544e6d6f5e3")
  val bodyId = UUID.fromString("327068c7-a650-4f40-8f7d-c650d5acd7b0")

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
      val data: client.GraphqlData = client.GraphqlData(Some(
        gs.Data(List(gs.GetSeries(seriesId, bodyId, "name", "code", Option.empty)))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val seriesService = new SeriesService(graphQLConfiguration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration,
        seriesService, consignmentService)
      val seriesDetailsPage = controller.seriesDetails(consignmentId).apply(FakeRequest(GET, "/series").withCSRFToken)

      playStatus(seriesDetailsPage) mustBe OK
      contentType(seriesDetailsPage) mustBe Some("text/html")
      contentAsString(seriesDetailsPage) must include ("Series Information")
      contentAsString(seriesDetailsPage) must include ("Choose a series")
      contentAsString(seriesDetailsPage) must include ("Please choose an existing series reference for the records you would like to transfer.")
      contentAsString(seriesDetailsPage) must include ("id=\"series\"")
      contentAsString(seriesDetailsPage) must include (s"""<option value="${seriesId.toString}">code</option>""")

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val seriesService = new SeriesService(graphQLConfiguration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new SeriesDetailsController(getUnauthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration,
        seriesService, consignmentService)
      val seriesDetailsPage = controller.seriesDetails(consignmentId).apply(FakeRequest(GET, "/series"))
      redirectLocation(seriesDetailsPage).get must startWith ("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(seriesDetailsPage) mustBe FOUND
    }

    "render an error if the api returns errors" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val seriesService = new SeriesService(graphQLConfiguration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration,
        seriesService, consignmentService)
      val seriesDetailsPage = controller.seriesDetails(consignmentId).apply(FakeRequest(GET, "/series"))

      val failure = seriesDetailsPage.failed.futureValue
      failure mustBe an[GraphQlException]
    }

    "render the error page if the token is invalid" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val seriesService = new SeriesService(graphQLConfiguration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Body does not match", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents, getInvalidKeycloakConfiguration,
        seriesService, consignmentService)
      val seriesDetailsPage = controller.seriesDetails(consignmentId).apply(FakeRequest(GET, "/series"))

      val failure = seriesDetailsPage.failed.futureValue

      failure.getMessage should include("Token not provided")
    }

    //TODO update this test to use the updateconsignmentseriesId graphql
    "update series id for consignment when a valid form is submitted and the api response is successful" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[ac.Data, ac.Variables]()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val seriesService = new SeriesService(graphQLConfiguration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val seriesId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val consignmentResponse: ac.AddConsignment = new ac.AddConsignment(Some(consignmentId), Some(seriesId))
      val data: client.GraphqlData = client.GraphqlData(Some(ac.Data(consignmentResponse)), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration,
        seriesService, consignmentService)
      val seriesSubmit = controller.seriesSubmit(consignmentId).apply(FakeRequest().withFormUrlEncodedBody(("series", seriesId.toString)).withCSRFToken)
      playStatus(seriesSubmit) mustBe SEE_OTHER
      redirectLocation(seriesSubmit) must be(Some(s"/consignment/$consignmentId/transfer-agreement"))
    }

    //TODO Fix this test
    "renders an error when a valid form is submitted but there is an error from the api" in {
      val seriesId = UUID.randomUUID()
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val seriesService = new SeriesService(graphQLConfiguration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))
      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration,
        seriesService, consignmentService)
      val seriesSubmit = controller.seriesSubmit(consignmentId)
        .apply(FakeRequest(POST, "/series")
          .withFormUrlEncodedBody(("series", seriesId.toString))
          .withCSRFToken)

      seriesSubmit.failed.futureValue shouldBe a[GraphQlException]
    }

    //TODO remove this test if "error from api for submit" test above passes
    "renders an error when a valid form is submitted but there is an error from the api2" in {
      updateSeriesId(Future.failed(new RuntimeException("graphql error"))).failed.futureValue.getMessage should equal("graphql error")
    }

    "display errors when an invalid form is submitted" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Some(
        gs.Data(List(gs.GetSeries(seriesId, bodyId, "name", "code", Option.empty)))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val seriesService = new SeriesService(graphQLConfiguration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration,
        seriesService, consignmentService)
      val seriesSubmit = controller.seriesSubmit(consignmentId).apply(FakeRequest(POST, "/series").withCSRFToken)
      playStatus(seriesSubmit) mustBe BAD_REQUEST
      contentAsString(seriesSubmit) must include("govuk-error-message")
      contentAsString(seriesSubmit) must include("error.required")
      contentAsString(seriesSubmit) must include("govuk-error-summary")
      contentAsString(seriesSubmit) must include("<title>Error: ")
    }

    "will send the correct body if it is present on the user" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Some(
        gs.Data(List(gs.GetSeries(seriesId, bodyId, "name", "code", Option.empty)))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val seriesService = new SeriesService(graphQLConfiguration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration,
        seriesService, consignmentService)
      controller.seriesDetails(consignmentId).apply(FakeRequest(GET, "/series").withCSRFToken).futureValue

      val expectedJson = "{\"query\":\"query getSeries($body:String!)" +
        "{getSeries(body:$body){seriesid bodyid name code description}}\",\"variables\":{\"body\":\"Body\"}}"
      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")).withRequestBody(equalToJson(expectedJson)))
    }

    "will return forbidden if the pages are accessed by a judgment user" in {
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val seriesService = new SeriesService(graphQLConfiguration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)

      val controller = new SeriesDetailsController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration,
        seriesService, consignmentService)

      val seriesGet = controller.seriesDetails(consignmentId).apply(FakeRequest(GET, "/series").withCSRFToken)
      val seriesPost = controller.seriesSubmit(consignmentId).apply(FakeRequest().withFormUrlEncodedBody(("series", seriesId.toString)).withCSRFToken)
      playStatus(seriesGet) mustBe FORBIDDEN
      playStatus(seriesPost) mustBe FORBIDDEN
    }
  }

  private def updateSeriesId(getResultResponse: Future[GraphQlResponse[Data]]): Future[Boolean] = {
    val graphQLConfiguration = mock[GraphQLConfiguration]
    val client = mock[GraphQLClient[Data, Variables]]
    when(client.getResult[Future](any[BearerAccessToken], any[Document], any[Option[Variables]])
      (any[SttpBackend[Future, Nothing, NothingT]], any[ClassTag[Future[_]]]))
      .thenReturn(getResultResponse)
    when(graphQLConfiguration.getClient[Data, Variables]()).thenReturn(client)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    consignmentService.updateSeriesIdOfConsignment(consignmentId, seriesId, new BearerAccessToken())
  }
}
