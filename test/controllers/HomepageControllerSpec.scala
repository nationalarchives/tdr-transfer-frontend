package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, serverError, urlEqualTo}
import configuration.{ApplicationConfig, GraphQLConfiguration}
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
import play.api.Play.materializer

import java.util.UUID
import scala.concurrent.ExecutionContext

class HomepageControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  lazy val graphqlConfig = new GraphQLConfiguration(app.configuration)
  lazy val consignmentService = new ConsignmentService(graphqlConfig)
  lazy val config = new ApplicationConfig(app.configuration)

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements
  val viewTransferButton: String =
    """
      |<a href="/view-transfers" role="button" draggable="false" class="govuk-button" data-module="govuk-button">
      |    View transfers
      |</a>""".stripMargin

  val transfersForReviewButton: String =
    """<a href="/admin/metadata-review" role="button" draggable="false" class="govuk-button" data-module="govuk-button">Transfers for Review</a>"""

  "HomepageController GET" should {

    "render the registration complete page with an authenticated user with no user type" in {
      val controller = new HomepageController(
        getAuthorisedSecurityComponents,
        getValidKeycloakConfiguration,
        consignmentService,
        config
      )
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage"))
      val homepagePageAsString = contentAsString(homepagePage)

      status(homepagePage) mustBe OK
      contentType(homepagePage) mustBe Some("text/html")
      homepagePageAsString must include("Thank you for completing your registration")
      homepagePageAsString must include("Next Steps")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(homepagePageAsString, userType = "", consignmentExists = false)
    }

    "render the homepage page with an authenticated standard user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration, consignmentService, config)
      val userType = "standard"
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage").withCSRFToken)
      val homepagePageAsString = contentAsString(homepagePage)

      status(homepagePage) mustBe OK
      contentType(homepagePage) mustBe Some("text/html")

      homepagePageAsString must include("Upload your records to start a new transfer")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(homepagePageAsString, userType = userType, consignmentExists = false)
      checkForContentOnHomepagePage(homepagePageAsString, userType = userType)
      homepagePageAsString must include(viewTransferButton)
      homepagePageAsString must not include transfersForReviewButton
    }

    "render the judgment homepage page with an authenticated judgment user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration, consignmentService, config)
      val userType = "judgment"
      val homepagePage = controller.judgmentHomepage().apply(FakeRequest(GET, s"/$userType/homepage").withCSRFToken)
      val homepagePageAsString = contentAsString(homepagePage)
      status(homepagePage) mustBe OK
      contentType(homepagePage) mustBe Some("text/html")

      homepagePageAsString must include("Welcome to the Transfer Digital Records service")
      homepagePageAsString must include("You can use this service to:")
      homepagePageAsString must include("transfer judgments and decisions")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(homepagePageAsString, userType = userType, consignmentExists = false)
      checkForContentOnHomepagePage(homepagePageAsString, userType = userType)
      homepagePageAsString must not include viewTransferButton
      homepagePageAsString must not include transfersForReviewButton
    }

    "render the DTA review homepage page with an authenticated tna user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), consignmentService, config)
      val userType = "tna"
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage").withCSRFToken)
      val homepagePageAsString = contentAsString(homepagePage)

      status(homepagePage) mustBe OK
      contentType(homepagePage) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(homepagePageAsString, userType = userType, consignmentExists = false)
      homepagePageAsString must include(transfersForReviewButton)
    }

    "return a redirect to the judgment homepage page with an authenticated judgment user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration, consignmentService, config)
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage").withCSRFToken)

      status(homepagePage) mustBe SEE_OTHER
      redirectLocation(homepagePage) must be(Some("/judgment/homepage"))
    }

    "return a redirect to the standard homepage page with an authenticated standard user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration, consignmentService, config)
      val homepagePage = controller.judgmentHomepage().apply(FakeRequest(GET, "/judgment/homepage").withCSRFToken)

      status(homepagePage) mustBe SEE_OTHER
      redirectLocation(homepagePage) must be(Some("/homepage"))
    }

    "return a redirect from the standard homepage to the auth server with an unauthenticated user" in {
      val controller = new HomepageController(getUnauthorisedSecurityComponents, getValidKeycloakConfiguration, consignmentService, config)
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/homepage"))
      redirectLocation(homepagePage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      status(homepagePage) mustBe FOUND
    }

    "return a redirect from the judgment homepage to the auth server with an unauthenticated user" in {
      val controller = new HomepageController(getUnauthorisedSecurityComponents, getValidKeycloakConfiguration, consignmentService, config)
      val homepagePage = controller.homepage().apply(FakeRequest(GET, "/judgment/homepage"))
      redirectLocation(homepagePage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      status(homepagePage) mustBe FOUND
    }
  }

  "HomepageController POST" should {
    "create a new consignment for a judgment user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration, consignmentService, config)

      val consignmentId = UUID.fromString("6c5756a9-dd7a-437c-9396-33b227e53768")
      val addConsignment = AddConsignment(Option(consignmentId), None, "Consignment-Ref")
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
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidJudgmentUserKeycloakConfiguration, consignmentService, config)
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
      val controller = new HomepageController(getUnauthorisedSecurityComponents, getValidKeycloakConfiguration, consignmentService, config)
      val homepagePage = controller.homepage().apply(FakeRequest(POST, "/homepage").withCSRFToken)
      redirectLocation(homepagePage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      status(homepagePage) mustBe SEE_OTHER
    }

    "create a new consignment for a standard user" in {
      val controller = new HomepageController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration, consignmentService, config)

      val consignmentId = UUID.fromString("6c5756a9-dd7a-437c-9396-33b227e53768")
      val addConsignment = AddConsignment(Option(consignmentId), None, "Consignment-Ref")
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
    pageAsString must include("Welcome to the Transfer Digital Records service")
    pageAsString must include("<title>Welcome to the Transfer Digital Records service - Transfer Digital Records - GOV.UK</title>")
    if (userType == "judgment") {
      pageAsString must include("""<h1 class="govuk-heading-xl">Welcome to the Transfer Digital Records service</h1>""")
      pageAsString must include("""<p class="govuk-body">You can use this service to:</p>""")
      pageAsString must include("""<ul class="govuk-list govuk-list--bullet">
       |          <li>transfer judgments and decisions</li>
       |          <li>transfer an amendment to an existing judgment or decision</li>
       |        </ul>""".stripMargin)
      pageAsString must include("Start your transfer")
      pageAsString must include("""<form action="/judgment/homepage" method="POST" novalidate="">""")
      pageAsString must include("""<h2 class="govuk-heading-m">Service update – September 2025</h2>""")
      pageAsString must include("""<p class="govuk-body">You can now upload amendments and press summaries to existing judgments or decisions.</p>""")
      pageAsString must include("""<p class="govuk-body">When you select "Start your transfer", choose the document type and enter the Neutral Citation Number (NCN) of the original judgment or decision.</p>""")
      pageAsString must include("""<p class="govuk-body">If there’s no NCN, you’ll need to provide extra details.</p>""")
      pageAsString must include("""<h2 class="govuk-heading-m">Further support</h2>""")
    } else {
      pageAsString must include("Start transfer")
      pageAsString must include("""<form action="/homepage" method="POST" novalidate="">""")
    }
  }
}
