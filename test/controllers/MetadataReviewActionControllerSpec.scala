package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignmentDetailsForMetadataReview.getConsignmentDetailsForMetadataReview
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.mockito.ArgumentMatcher
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.{times, verify}
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status, status => playStatus, _}
import services.MessagingService.MetadataReviewSubmittedEvent
import services.Statuses.CompletedValue
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.util.UUID
import scala.concurrent.ExecutionContext

class MetadataReviewActionControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val userId: UUID = UUID.randomUUID()

  val wiremockServer = new WireMockServer(9006)
  val messagingService: MessagingService = mock[MessagingService]

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements

  "MetadataReviewActionController GET" should {

    "render the correct metadata details page with an authenticated user" in {
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

    "Update the consignment status and send MetadataReviewSubmittedEvent message when a valid form is submitted and the api response is successful" in {
      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration)
      val consignmentRef = "TDR-TEST-2024"
      val userEmail = "test@test.com"
      val status = CompletedValue.value
      val expectedPath = s"/consignment/$consignmentId/metadata-review/review-progress"

      // Custom ArgumentMatcher to match the event based on the consignment reference, path, userEmail and status
      class MetadataReviewSubmittedEventMatcher(expectedConsignmentRef: String, expectedPath: String, expectedEmail: String, expectedStatus: String)
          extends ArgumentMatcher[MetadataReviewSubmittedEvent] {
        override def matches(event: MetadataReviewSubmittedEvent): Boolean = {
          event.consignmentReference == expectedConsignmentRef && event.urlLink.contains(expectedPath) && event.userEmail == expectedEmail && event.status == expectedStatus
        }
      }

      val metadataReviewDecisionEventMatcher = new MetadataReviewSubmittedEventMatcher(consignmentRef, expectedPath, userEmail, status)

      setUpdateConsignmentStatus(wiremockServer)

      val reviewSubmit =
        controller.submitReview(consignmentId, consignmentRef, userEmail).apply(FakeRequest().withFormUrlEncodedBody(("status", status)).withCSRFToken)
      playStatus(reviewSubmit) mustBe SEE_OTHER
      redirectLocation(reviewSubmit) must be(Some(s"/admin/metadata-review"))
      verify(messagingService, times(1)).sendMetadataReviewSubmittedNotification(argThat(metadataReviewDecisionEventMatcher))
    }

    "display errors when an invalid form is submitted" in {
      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration)
      val consignmentRef = "TDR-TEST-2024"
      val userEmail = "test@test.com"
      val metadataReviewDecisionEvent = MetadataReviewSubmittedEvent(consignmentRef, "SomeUrl", userEmail, "status")
      val reviewSubmit = controller.submitReview(consignmentId, consignmentRef, userEmail).apply(FakeRequest().withFormUrlEncodedBody(("status", "")).withCSRFToken)
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
      verify(messagingService, times(0)).sendMetadataReviewSubmittedNotification(metadataReviewDecisionEvent)
    }
  }

  private def instantiateMetadataReviewActionController(
      securityComponents: SecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration
  ) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

    new MetadataReviewActionController(securityComponents, keycloakConfiguration, consignmentService, consignmentStatusService, messagingService)
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
      |                            Contact email
      |                        </dt>
      |                        <dd class="govuk-summary-list__value">
      |                        email@test.com
      |                        </dd>""".stripMargin)
    pageAsString must include("""1. Download and review transfer metadata""")
    pageAsString must include(downloadLinkHTML(consignmentId))
    pageAsString must include("""2. Set the status of this review""")
    pageAsString must include(s"""<form action="/admin/metadata-review/$consignmentId?consignmentRef=TDR-2024-TEST&amp;userEmail=email%40test.com" method="POST" novalidate="">""")
    pageAsString must include(s"""<option value="" selected>
     |                    Select a status
     |                </option>""".stripMargin)
    pageAsString must include(s"""<option value="Completed">Approve</option>""")
    pageAsString must include(s"""<option value="CompletedWithIssues">Reject</option>""")
  }

  private def downloadLinkHTML(consignmentId: UUID): String = {
    val linkHTML: String = s"""<a class="govuk-button  govuk-button--secondary govuk-!-margin-bottom-8 download-metadata" href="/consignment/$consignmentId/additional-metadata/download-metadata/csv">
                              |    <span aria-hidden="true" class="tna-button-icon">
                              |        <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 23 23">
                              |            <path fill="#020202" d="m11.5 16.75-6.563-6.563 1.838-1.903 3.412 3.413V1h2.626v10.697l3.412-3.413 1.837 1.903L11.5 16.75ZM3.625 22c-.722 0-1.34-.257-1.853-.77A2.533 2.533 0 0 1 1 19.375v-3.938h2.625v3.938h15.75v-3.938H22v3.938c0 .722-.257 1.34-.77 1.855a2.522 2.522 0 0 1-1.855.77H3.625Z"></path>
                              |        </svg>
                              |    </span>
                              |    Download metadata
                              |</a>
                              |""".stripMargin
    linkHTML
  }
}
