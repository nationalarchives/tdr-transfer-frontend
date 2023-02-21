package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import errors.GraphQlException
import graphql.codegen.AddConsignment.{addConsignment => ac}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.GetSeries.{getSeries => gs}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers._
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.{ConsignmentService, ConsignmentStatusService, SeriesService}
import uk.gov.nationalarchives.tdr.GraphQLClient
import testUtils.{CheckPageForStaticElements, FormTester, FrontEndTestHelper}
import testUtils.DefaultMockFormOptions.{MockInputOption, getExpectedSeriesDefaultOptions}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.collection.immutable.TreeMap
import scala.concurrent.ExecutionContext

class SeriesDetailsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val seriesId: UUID = UUID.fromString("1ebba1f2-5cd9-4379-a26e-f544e6d6f5e3")
  val bodyId: UUID = UUID.fromString("327068c7-a650-4f40-8f7d-c650d5acd7b0")

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val expectedSeriesDefaultOptions: List[MockInputOption] = getExpectedSeriesDefaultOptions(seriesId)

  val checkPageForStaticElements = new CheckPageForStaticElements

  "SeriesDetailsController GET" should {

    "render the correct series details page with an authenticated user" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Some(gs.Data(List(gs.GetSeries(seriesId, bodyId, "name", "MOCK1", Option.empty)))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(dataString))
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, Some(seriesId))
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val controller = instantiateSeriesController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails(consignmentId).apply(FakeRequest(GET, "/series").withCSRFToken)

      val seriesDetailsPageAsString = contentAsString(seriesDetailsPage)

      playStatus(seriesDetailsPage) mustBe OK
      contentType(seriesDetailsPage) mustBe Some("text/html")
      seriesDetailsPageAsString must include("<title>Series Information</title>")
      checkForExpectedSeriesPageContent(seriesDetailsPageAsString)

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(seriesDetailsPageAsString, userType = "standard")

      seriesDetailsPageAsString should include("""<select class="govuk-select" id="series" name="series"  >""")
      seriesDetailsPageAsString should include(s"""<option value="${seriesId.toString}">MOCK1</option>""")
      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = instantiateSeriesController(getUnauthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails(consignmentId).apply(FakeRequest(GET, "/series"))
      redirectLocation(seriesDetailsPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(seriesDetailsPage) mustBe FOUND
    }

    "render an error if the api returns errors" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(dataString))
      )

      val controller = instantiateSeriesController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails(consignmentId).apply(FakeRequest(GET, "/series"))

      val failure = seriesDetailsPage.failed.futureValue
      failure mustBe an[GraphQlException]
    }

    "render the error page if the token is invalid" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Body does not match", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(dataString))
      )

      val controller = instantiateSeriesController(getAuthorisedSecurityComponents, getInvalidKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails(consignmentId).apply(FakeRequest(GET, "/series"))

      val failure = seriesDetailsPage.failed.futureValue

      failure.getMessage should include("Token not provided")
    }

    "create a consignment when a valid form is submitted and the api response is successful" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[ac.Data, ac.Variables]()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val seriesService = new SeriesService(graphQLConfiguration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
      val consignmentId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val seriesId = UUID.fromString("c2efd3e6-6664-4582-8c28-dcf891f60e68")
      val consignmentResponse: ac.AddConsignment = new ac.AddConsignment(Some(consignmentId), Some(seriesId))
      val data: client.GraphqlData = client.GraphqlData(Some(ac.Data(consignmentResponse)), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(dataString))
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, Some(seriesId))
      setConsignmentTypeResponse(wiremockServer, "standard")

      val controller =
        new SeriesDetailsController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration, seriesService, consignmentService, consignmentStatusService)
      val seriesSubmit = controller.seriesSubmit(consignmentId).apply(FakeRequest().withFormUrlEncodedBody(("series", seriesId.toString)).withCSRFToken)
      playStatus(seriesSubmit) mustBe SEE_OTHER
      redirectLocation(seriesSubmit) must be(Some(s"/consignment/$consignmentId/transfer-agreement"))
    }

    "renders an error when a valid form is submitted but there is an error from the api" in {
      val seriesId = UUID.randomUUID()
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Option.empty, List(GraphQLClient.Error("Error", Nil, Nil, None)))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(dataString))
      )

      val controller = instantiateSeriesController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val seriesSubmit = controller
        .seriesSubmit(consignmentId)
        .apply(
          FakeRequest(POST, s"/consignment/$consignmentId/series")
            .withFormUrlEncodedBody(("series", seriesId.toString))
            .withCSRFToken
        )

      seriesSubmit.failed.futureValue shouldBe a[GraphQlException]
    }

    "display errors when an invalid form is submitted" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Some(gs.Data(List(gs.GetSeries(seriesId, bodyId, "name", "MOCK1", Option.empty)))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(dataString))
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, Some(seriesId))
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val controller = instantiateSeriesController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val seriesSubmit = controller.seriesSubmit(consignmentId).apply(FakeRequest(POST, "/series").withCSRFToken)
      playStatus(seriesSubmit) mustBe BAD_REQUEST

      val seriesSubmitAsString = contentAsString(seriesSubmit)

      contentType(seriesSubmit) mustBe Some("text/html")
      contentAsString(seriesSubmit) must include("<title>Error: Series Information</title>")
      seriesSubmitAsString must include("class=\"govuk-visually-hidden\">Error:")
      checkForExpectedSeriesPageContent(seriesSubmitAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(seriesSubmitAsString, userType = "standard")

      seriesSubmitAsString should include("""<select class="govuk-select" id="series" name="series"  >""")
      seriesSubmitAsString should include(s"""<option value="${seriesId.toString}">MOCK1</option>""")
    }

    "send the correct body if it is present on the user" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
      val data: client.GraphqlData = client.GraphqlData(Some(gs.Data(List(gs.GetSeries(seriesId, bodyId, "name", "MOCK1", Option.empty)))))
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(dataString))
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, Some(seriesId))
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val controller = instantiateSeriesController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      controller.seriesDetails(consignmentId).apply(FakeRequest(GET, "/series").withCSRFToken).futureValue

      val expectedJson = "{\"query\":\"query getSeries($body:String!)" +
        "{getSeries(body:$body){seriesid bodyid name code description}}\",\"variables\":{\"body\":\"Body\"}}"
      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")).withRequestBody(equalToJson(expectedJson)))
    }

    "return forbidden if the pages are accessed by a judgment user" in {
      mockGetSeries()
      setConsignmentStatusResponse(app.configuration, wiremockServer, Some(seriesId), seriesStatus = None)
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val controller = instantiateSeriesController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration)
      val seriesGet = controller.seriesDetails(consignmentId).apply(FakeRequest(GET, "/series").withCSRFToken)
      val seriesPost = controller.seriesSubmit(consignmentId).apply(FakeRequest().withFormUrlEncodedBody(("series", seriesId.toString)).withCSRFToken)
      playStatus(seriesGet) mustBe FORBIDDEN
      playStatus(seriesPost) mustBe FORBIDDEN
    }

    "render the series 'already chosen' page with an authenticated user if series status is 'Completed'" in {
      val controller = instantiateSeriesController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val seriesDetailsPage = controller.seriesDetails(consignmentId).apply(FakeRequest(GET, f"/consignment/$consignmentId/series").withCSRFToken)
      val someDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())
      val consignmentStatuses = List(ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), "Series", "Completed", someDateTime, None))

      setConsignmentStatusResponse(app.configuration, wiremockServer, Some(seriesId), consignmentStatuses = consignmentStatuses)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val seriesDetailsPageAsString = contentAsString(seriesDetailsPage)
      val expectedOptions: List[MockInputOption] = List(
        MockInputOption(
          name = "series",
          id = "series",
          label = "MOCK1",
          value = s"$seriesId",
          fieldType = "inputDropdown",
          errorMessage = "error.required"
        )
      )
      val formTester = new FormTester(expectedOptions) // placeholder is not an option on "already chosen" page

      playStatus(seriesDetailsPage) mustBe OK
      contentType(seriesDetailsPage) mustBe Some("text/html")
      headers(seriesDetailsPage) mustBe TreeMap("Cache-Control" -> "no-store, must-revalidate")
      seriesDetailsPageAsString must include("You have already chosen a series reference")
      seriesDetailsPageAsString must include("Click 'Continue' to proceed with your transfer.")

      checkForExpectedSeriesPageContent(seriesDetailsPageAsString, seriesAlreadyChosen = true)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(seriesDetailsPageAsString, userType = "standard")
      seriesDetailsPageAsString should include("""<select class="govuk-select" id="series" name="series"  disabled>""")
      seriesDetailsPageAsString should include(s"""<option selected="selected" value="${seriesId.toString}">MOCK1</option>""")
    }
  }

  private def instantiateSeriesController(securityComponents: SecurityComponents, keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val seriesService = new SeriesService(graphQLConfiguration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

    new SeriesDetailsController(securityComponents, keycloakConfiguration, seriesService, consignmentService, consignmentStatusService)
  }

  private def mockGetSeries(): StubMapping = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gs.Data, gs.Variables]()
    val data: client.GraphqlData = client.GraphqlData(Some(gs.Data(List(gs.GetSeries(seriesId, bodyId, "name", "MOCK1", Option.empty)))))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString))
    )
  }

  private def checkForExpectedSeriesPageContent(pageAsString: String, seriesAlreadyChosen: Boolean = false): Unit = {
    pageAsString must include("Choose a series")
    pageAsString must include("Please choose an existing series reference for the records you would like to transfer.")

    if (seriesAlreadyChosen) {
      pageAsString must include("""<select class="govuk-select" id="series" name="series"  disabled>""")
    } else {
      pageAsString must include("""<select class="govuk-select" id="series" name="series"  >""")
    }
  }
}
