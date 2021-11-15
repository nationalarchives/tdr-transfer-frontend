package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, serverError, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.AddConsignment.addConsignment.{AddConsignment, Data, Variables}
import org.scalatest.Matchers._
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status, _}
import services.ConsignmentService
import util.FrontEndTestHelper
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.concurrent.ScalaFutures._

import java.util.UUID
import scala.concurrent.ExecutionContext

class DashboardControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  lazy val graphqlConfig = new GraphQLConfiguration(app.configuration)
  lazy val consignmentService = new ConsignmentService(graphqlConfig)

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "DashboardController GET" should {

    "render the dashboard page with an authenticated user with no user type" in {
      val controller = new DashboardController(
        getAuthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        consignmentService
      )
      val dashboardPage = controller.dashboard().apply(FakeRequest(GET, "/dashboard"))
      status(dashboardPage) mustBe OK
      contentType(dashboardPage) mustBe Some("text/html")
      contentAsString(dashboardPage) must include ("Welcome")
      contentAsString(dashboardPage) must include ("Welcome to the Transfer Digital Records service")
      contentAsString(dashboardPage) must include ("Upload your records to start a new transfer")
    }

    "render the dashboard page with an authenticated standard user" in {
      val controller = new DashboardController(
        getAuthorisedSecurityComponents,
        getValidStandardUserKeycloakConfiguration,
        consignmentService)
      val dashboardPage = controller.dashboard().apply(FakeRequest(GET, "/dashboard"))
      status(dashboardPage) mustBe OK
      contentType(dashboardPage) mustBe Some("text/html")
      contentAsString(dashboardPage) must include ("Welcome")
      contentAsString(dashboardPage) must include ("Welcome to the Transfer Digital Records service")
      contentAsString(dashboardPage) must include ("Upload your records to start a new transfer")
    }

    "render the judgment dashboard page with an authenticated judgment user" in {
      val controller = new DashboardController(
        getAuthorisedSecurityComponents,
        getValidJudgmentUserKeycloakConfiguration,
        consignmentService)
      val dashboardPage = controller.dashboard().apply(FakeRequest(GET, "/dashboard").withCSRFToken)
      status(dashboardPage) mustBe OK
      contentType(dashboardPage) mustBe Some("text/html")

      contentAsString(dashboardPage) must include ("Welcome")
      contentAsString(dashboardPage) must include ("Welcome to the Transfer Digital Records service")
      contentAsString(dashboardPage) must include ("Upload your judgment to start a new transfer")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new DashboardController(
        getUnauthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        consignmentService)
      val dashboardPage = controller.dashboard().apply(FakeRequest(GET, "/dashboard"))
      redirectLocation(dashboardPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      status(dashboardPage) mustBe FOUND
    }
  }

  "DashboardController POST" should {
    "create a new consignment for a judgment user" in {
      val controller = new DashboardController(
        getAuthorisedSecurityComponents,
        getValidJudgmentUserKeycloakConfiguration,
        consignmentService)

      val consignmentId = UUID.fromString("6c5756a9-dd7a-437c-9396-33b227e53768")
      val addConsignment = AddConsignment(Option(consignmentId), None)
      val client = graphqlConfig.getClient[Data, Variables]()
      val dataString = client.GraphqlData(Option(Data(addConsignment)))
        .asJson.printWith(Printer(dropNullValues = false, ""))

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val redirect = controller.judgmentDashboardSubmit()
        .apply(FakeRequest(POST, "/dashboard").withCSRFToken)

      redirectLocation(redirect).get must equal(s"/judgment/$consignmentId/transfer-agreement")
      wiremockServer.getAllServeEvents.size should equal(1)
    }

    "show an error if the consignment couldn't be created" in {
      val controller = new DashboardController(
        getAuthorisedSecurityComponents,
        getValidJudgmentUserKeycloakConfiguration,
        consignmentService)
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(serverError()))

      val response: Throwable = controller.judgmentDashboardSubmit()
        .apply(FakeRequest(POST, "/dashboard").withCSRFToken).failed.futureValue

      response.getMessage.contains("Unexpected response from GraphQL API") should be(true)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new DashboardController(
        getUnauthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        consignmentService)
      val dashboardPage = controller.dashboard().apply(FakeRequest(POST, "/dashboard").withCSRFToken)
      redirectLocation(dashboardPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      status(dashboardPage) mustBe SEE_OTHER
    }
  }
}
