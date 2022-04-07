package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, serverError, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.AddConsignment.addConsignment.{AddConsignment, Data, Variables}
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status, _}
import services.ConsignmentService
import util.FrontEndTestHelper
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers._
import java.util.UUID
import scala.concurrent.ExecutionContext

class HomepageControllerSpec extends FrontEndTestHelper {
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

  "HomepageController GET" should {

    "render the registration complete page with an authenticated user with no user type" in {
      val controller = new HomepageController(
        getAuthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        consignmentService
      )
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage"))
      status(homepagePage) mustBe OK
      contentType(homepagePage) mustBe Some("text/html")
      contentAsString(homepagePage) must include ("Registration Complete")
      contentAsString(homepagePage) must include ("Thank you for completing your registration")
    }

    "render the homepage page with an authenticated standard user" in {
      val controller = new HomepageController(
        getAuthorisedSecurityComponents,
        getValidStandardUserKeycloakConfiguration,
        consignmentService)
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage").withCSRFToken)
      status(homepagePage) mustBe OK
      contentType(homepagePage) mustBe Some("text/html")
      contentAsString(homepagePage) must include ("Welcome")
      contentAsString(homepagePage) must include ("Welcome to the Transfer Digital Records service")
      contentAsString(homepagePage) must include ("Upload your records to start a new transfer")
    }

    "render the judgment homepage page with an authenticated judgment user" in {
      val controller = new HomepageController(
        getAuthorisedSecurityComponents,
        getValidJudgmentUserKeycloakConfiguration,
        consignmentService)
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage").withCSRFToken)
      status(homepagePage) mustBe OK
      contentType(homepagePage) mustBe Some("text/html")

      contentAsString(homepagePage) must include ("Welcome")
      contentAsString(homepagePage) must include ("Welcome to the Transfer Digital Records service")
      contentAsString(homepagePage) must include ("Before you start")
      contentAsString(homepagePage) must include ("You must upload your judgment as a Microsoft Word (.docx) document. Any other formats will not be accepted.")
      contentAsString(homepagePage) must include ("Upload your judgment to start a new transfer")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new HomepageController(
        getUnauthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        consignmentService)
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage"))
      redirectLocation(homepagePage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      status(homepagePage) mustBe FOUND
    }
  }

  "HomepageController POST" should {
    "create a new consignment for a judgment user" in {
      val controller = new HomepageController(
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

      val redirect = controller.judgmentHomepageSubmit()
        .apply(FakeRequest(POST, "/judgment/homepage").withCSRFToken)

      redirectLocation(redirect).get must equal(s"/judgment/$consignmentId/before-uploading")
      wiremockServer.getAllServeEvents.size should equal(1)
    }

    "show an error if the consignment couldn't be created" in {
      val controller = new HomepageController(
        getAuthorisedSecurityComponents,
        getValidJudgmentUserKeycloakConfiguration,
        consignmentService)
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(serverError()))

      val response: Throwable = controller.judgmentHomepageSubmit()
        .apply(FakeRequest(POST, "/homepage").withCSRFToken).failed.futureValue

      response.getMessage.contains("Unexpected response from GraphQL API") should be(true)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new HomepageController(
        getUnauthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        consignmentService)
      val homepagePage = controller.homepage().apply(FakeRequest(POST, "/homepage").withCSRFToken)
      redirectLocation(homepagePage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      status(homepagePage) mustBe SEE_OTHER
    }

    "create a new consignment for a standard user" in {
      val controller = new HomepageController(
        getAuthorisedSecurityComponents,
        getValidStandardUserKeycloakConfiguration,
        consignmentService)

      val consignmentId = UUID.fromString("6c5756a9-dd7a-437c-9396-33b227e53768")
      val addConsignment = AddConsignment(Option(consignmentId), None)
      val client = graphqlConfig.getClient[Data, Variables]()
      val dataString = client.GraphqlData(Option(Data(addConsignment)))
        .asJson.printWith(Printer(dropNullValues = false, ""))

      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString)))

      val redirect = controller.homepageSubmit()
        .apply(FakeRequest(POST, "/homepage").withCSRFToken)

      redirectLocation(redirect).get must equal(s"/consignment/$consignmentId/series")
      wiremockServer.getAllServeEvents.size should equal(1)
    }
  }
}
