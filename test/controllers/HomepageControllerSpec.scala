package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, serverError, urlEqualTo}
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import configuration.GraphQLConfiguration
import graphql.codegen.AddConsignment.addConsignment.{AddConsignment, Data, Variables}
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status, _}
import services.ConsignmentService
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
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
  val configuration: Config = ConfigFactory.load()

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements

  "HomepageController GET" should {

    "render the registration complete page with an authenticated user with no user type" in {
      val controller = new HomepageController(
        getAuthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        consignmentService,
        configuration
      )
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage"))
      val homepagePageAsString = contentAsString(homepagePage)

      status(homepagePage) mustBe OK
      contentType(homepagePage) mustBe Some("text/html")
      homepagePageAsString must include("Registration Complete")
      homepagePageAsString must include("Thank you for completing your registration")
      homepagePageAsString must include("Next Steps")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(homepagePageAsString, userType = "", consignmentExists = false)
    }

    "render the homepage page with an authenticated standard user" in {
      val config: Config = ConfigFactory
        .load()
        .withValue("featureAccessBlock.viewTransfers", ConfigValueFactory.fromAnyRef("false"))
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration, consignmentService, config)
      val userType = "standard"
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage").withCSRFToken)
      val homepagePageAsString = contentAsString(homepagePage)

      status(homepagePage) mustBe OK
      contentType(homepagePage) mustBe Some("text/html")

      homepagePageAsString must include("Upload your records to start a new transfer")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(homepagePageAsString, userType = userType, consignmentExists = false)
      checkForContentOnHomepagePage(homepagePageAsString, userType = userType)
      homepagePageAsString must include("""
          |<a href="/view-transfers" role="button" draggable="false" class="govuk-button" data-module="govuk-button">
          |    View transfers
          |</a>""".stripMargin)
    }

    "render the judgment homepage page with an authenticated judgment user" in {
      val config: Config = ConfigFactory
        .load()
        .withValue("featureAccessBlock.viewTransfers", ConfigValueFactory.fromAnyRef("false"))
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration, consignmentService, config)
      val userType = "judgment"
      val homepagePage = controller.judgmentHomepage().apply(FakeRequest(GET, s"/$userType/homepage").withCSRFToken)
      val homepagePageAsString = contentAsString(homepagePage)
      status(homepagePage) mustBe OK
      contentType(homepagePage) mustBe Some("text/html")

      homepagePageAsString must include("Before you start")
      homepagePageAsString must include("You must upload your judgment as a Microsoft Word (.docx) document. Any other formats will not be accepted.")
      homepagePageAsString must include("Upload your judgment to start a new transfer")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(homepagePageAsString, userType = userType, consignmentExists = false)
      checkForContentOnHomepagePage(homepagePageAsString, userType = userType)
      homepagePageAsString must include("""
          |<a href="/view-transfers" role="button" draggable="false" class="govuk-button" data-module="govuk-button">
          |    View transfers
          |</a>""".stripMargin)
    }

    "render the homepage page without the view history button with an authenticated standard user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration, consignmentService, configuration)
      val userType = "standard"
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage").withCSRFToken)
      val homepagePageAsString = contentAsString(homepagePage)

      status(homepagePage) mustBe OK
      contentType(homepagePage) mustBe Some("text/html")

      homepagePageAsString must include("Upload your records to start a new transfer")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(homepagePageAsString, userType = userType, consignmentExists = false)
      checkForContentOnHomepagePage(homepagePageAsString, userType = userType)
      homepagePageAsString must not include
        """
          |<a href="/view-history" role="button" draggable="false" class="govuk-button" data-module="govuk-button">
          |    View transfers
          |</a>""".stripMargin
    }

    "render the homepage page without the view history button with an authenticated judgment user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration, consignmentService, configuration)
      val userType = "standard"
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage").withCSRFToken)
      val homepagePageAsString = contentAsString(homepagePage)

      status(homepagePage) mustBe OK
      contentType(homepagePage) mustBe Some("text/html")

      homepagePageAsString must include("Upload your records to start a new transfer")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(homepagePageAsString, userType = userType, consignmentExists = false)
      checkForContentOnHomepagePage(homepagePageAsString, userType = userType)
      homepagePageAsString must not include
        """
          |<a href="/view-history" role="button" draggable="false" class="govuk-button" data-module="govuk-button">
          |    View transfers
          |</a>""".stripMargin
    }

    "return a redirect to the judgment homepage page with an authenticated judgment user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration, consignmentService, configuration)
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage").withCSRFToken)

      status(homepagePage) mustBe SEE_OTHER
      redirectLocation(homepagePage) must be(Some("/judgment/homepage"))
    }

    "return a redirect to the standard homepage page with an authenticated standard user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration, consignmentService, configuration)
      val homepagePage = controller.judgmentHomepage().apply(FakeRequest(GET, "/judgment/homepage").withCSRFToken)

      status(homepagePage) mustBe SEE_OTHER
      redirectLocation(homepagePage) must be(Some("/homepage"))
    }

    "return a redirect from the standard homepage to the auth server with an unauthenticated user" in {
      val controller = new HomepageController(getUnauthorisedSecurityComponents, getValidKeycloakConfiguration, consignmentService, configuration)
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage"))
      redirectLocation(homepagePage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      status(homepagePage) mustBe FOUND
    }

    "return a redirect from the judgment homepage to the auth server with an unauthenticated user" in {
      val controller = new HomepageController(getUnauthorisedSecurityComponents, getValidKeycloakConfiguration, consignmentService, configuration)
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/judgment/homepage"))
      redirectLocation(homepagePage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      status(homepagePage) mustBe FOUND
    }
  }

  "HomepageController POST" should {
    "create a new consignment for a judgment user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration, consignmentService, configuration)

      val consignmentId = UUID.fromString("6c5756a9-dd7a-437c-9396-33b227e53768")
      val addConsignment = AddConsignment(Option(consignmentId), None)
      val client = graphqlConfig.getClient[Data, Variables]()
      val dataString = client.GraphqlData(Option(Data(addConsignment))).asJson.printWith(Printer(dropNullValues = false, ""))

      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(dataString))
      )

      val redirect = controller
        .judgmentHomepageSubmit()
        .apply(FakeRequest(POST, "/judgment/homepage").withCSRFToken)

      redirectLocation(redirect).get must equal(s"/judgment/$consignmentId/before-uploading")
      wiremockServer.getAllServeEvents.size should equal(1)
    }

    "show an error if the consignment couldn't be created" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration, consignmentService, configuration)
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(serverError())
      )

      val response: Throwable = controller
        .judgmentHomepageSubmit()
        .apply(FakeRequest(POST, "/homepage").withCSRFToken)
        .failed
        .futureValue

      response.getMessage must include("Unexpected response from GraphQL API")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = new HomepageController(getUnauthorisedSecurityComponents, getValidKeycloakConfiguration, consignmentService, configuration)
      val homepagePage = controller.homepage().apply(FakeRequest(POST, "/homepage").withCSRFToken)
      redirectLocation(homepagePage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      status(homepagePage) mustBe SEE_OTHER
    }

    "create a new consignment for a standard user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration, consignmentService, configuration)

      val consignmentId = UUID.fromString("6c5756a9-dd7a-437c-9396-33b227e53768")
      val addConsignment = AddConsignment(Option(consignmentId), None)
      val client = graphqlConfig.getClient[Data, Variables]()
      val dataString = client.GraphqlData(Option(Data(addConsignment))).asJson.printWith(Printer(dropNullValues = false, ""))

      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .willReturn(okJson(dataString))
      )

      val redirect = controller
        .homepageSubmit()
        .apply(FakeRequest(POST, "/homepage").withCSRFToken)

      redirectLocation(redirect).get must equal(s"/consignment/$consignmentId/series")
      wiremockServer.getAllServeEvents.size should equal(1)
    }
  }

  private def checkForContentOnHomepagePage(pageAsString: String, userType: String = "standard"): Unit = {
    pageAsString must include("<title>Welcome</title>")
    pageAsString must include("Welcome to the Transfer Digital Records service")
    pageAsString must include("Start transfer")
    if (userType == "judgment") {
      pageAsString must include("""<form action="/judgment/homepage" method="POST" novalidate="">""")
    } else {
      pageAsString must include("""<form action="/homepage" method="POST" novalidate="">""")
    }

  }
}
