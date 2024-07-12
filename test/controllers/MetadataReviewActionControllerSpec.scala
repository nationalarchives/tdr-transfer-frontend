package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignmentDetailsForMetadataReview.getConsignmentDetailsForMetadataReview
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, status => playStatus, _}
import services.Statuses.CompletedValue
import services.{ConsignmentService, ConsignmentStatusService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.util.UUID
import scala.concurrent.ExecutionContext

class MetadataReviewActionControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val userId: UUID = UUID.randomUUID()

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements

  "MetadataReviewActionController GET" should {

    "render the correct series details page with an authenticated user" in {
      setGetConsignmentDetailsForMetadataReviewResponse()

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration)
      val metadataReviewActionPage = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)

      val metadataReviewActionPageAsString = contentAsString(metadataReviewActionPage)

      playStatus(metadataReviewActionPage) mustBe OK
      contentType(metadataReviewActionPage) mustBe Some("text/html")

      checkForExpectedMetadataReviewActionPageContent(metadataReviewActionPageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataReviewActionPageAsString, userType = "tna")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = instantiateMetadataReviewActionController(getUnauthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration)
      val metadataReviewActionPage = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      redirectLocation(metadataReviewActionPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(metadataReviewActionPage) mustBe FOUND
    }

    "return 403 if the review metadata action page is accessed by a non TNA user" in {
      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidKeycloakConfiguration)
      val response = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)

      status(response) mustBe FORBIDDEN
    }

    "Update the consignment status when a valid form is submitted and the api response is successful" in {
      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration)
      setUpdateConsignmentStatus(wiremockServer)

      val reviewSubmit = controller.submitReview(consignmentId).apply(FakeRequest().withFormUrlEncodedBody(("status", CompletedValue.value)).withCSRFToken)
      playStatus(reviewSubmit) mustBe SEE_OTHER
      redirectLocation(reviewSubmit) must be(Some(s"/admin/metadata-review"))
    }

    "display errors when an invalid form is submitted" in {
      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration)
      val reviewSubmit = controller.submitReview(consignmentId).apply(FakeRequest().withFormUrlEncodedBody(("status", "")).withCSRFToken)
      setUpdateConsignmentStatus(wiremockServer)
      setGetConsignmentDetailsForMetadataReviewResponse()
      playStatus(reviewSubmit) mustBe BAD_REQUEST

      val metadataReviewSubmitAsString = contentAsString(reviewSubmit)

      contentType(reviewSubmit) mustBe Some("text/html")
      contentAsString(reviewSubmit) must include("<title>Error: View Request for Metadata - Transfer Digital Records - GOV.UK</title>")
      metadataReviewSubmitAsString must include("""<a href="#error-status">Select a status</a>""")
      metadataReviewSubmitAsString must include("""
      |    <p class="govuk-error-message" id="error-status">
      |        <span class="govuk-visually-hidden">Error:</span>
      |        Select a status
      |    </p>""".stripMargin)
      checkForExpectedMetadataReviewActionPageContent(metadataReviewSubmitAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataReviewSubmitAsString, userType = "tna")
    }
  }

  private def instantiateMetadataReviewActionController(
      securityComponents: SecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration
  ) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

    new MetadataReviewActionController(securityComponents, keycloakConfiguration, consignmentService, consignmentStatusService)
  }

  private def setGetConsignmentDetailsForMetadataReviewResponse() = {
    val client = new GraphQLConfiguration(app.configuration).getClient[getConsignmentDetailsForMetadataReview.Data, getConsignmentDetailsForMetadataReview.Variables]()
    val data: client.GraphqlData = client.GraphqlData(
      Some(
        getConsignmentDetailsForMetadataReview.Data(
          Some(
            getConsignmentDetailsForMetadataReview.GetConsignment(
              "TDR-2024-TEST",
              Some("SeriesName"),
              Some("TransferringBody"),
              userId
            )
          )
        )
      )
    )

    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentDetailsForMetadataReview"))
        .willReturn(okJson(dataString))
    )
  }

  private def checkForExpectedMetadataReviewActionPageContent(pageAsString: String): Unit = {
    pageAsString must include("""<a href="/admin/metadata-review" class="govuk-back-link">Back</a>""")
    pageAsString must include("""View request for TDR-2024-TEST""")
    pageAsString must include("""<dt class="govuk-summary-list__key">
      |                            Department
      |                        </dt>
      |                        <dd class="govuk-summary-list__value">
      |                        TransferringBody
      |                        </dd>""".stripMargin)
    pageAsString must include("""<dt class="govuk-summary-list__key">
      |                            Series
      |                        </dt>
      |                        <dd class="govuk-summary-list__value">
      |                        SeriesName
      |                        </dd>""".stripMargin)
    pageAsString must include(s"""<dt class="govuk-summary-list__key">
      |                            UserId
      |                        </dt>
      |                        <dd class="govuk-summary-list__value">
      |                        $userId
      |                        </dd>""".stripMargin)
    pageAsString must include("""1. Download and review transfer metadata""")
    pageAsString must include(
      s"""<a id="download-metadata" class="govuk-button govuk-button--secondary" href="/consignment/$consignmentId/additional-metadata/download-metadata/csv">
      |                    Download Metadata</a>""".stripMargin
    )
    pageAsString must include("""2. Set the status of this review""")
    pageAsString must include(s"""<form action="/admin/metadata-review/$consignmentId" method="POST" novalidate="">""")
    pageAsString must include(s"""<option value="" selected>
     |                    Select a status
     |                </option>""".stripMargin)
    pageAsString must include(s"""<option value="Completed">Approve</option>""")
    pageAsString must include(s"""<option value="CompletedWithIssues">Reject</option>""")
  }
}
