package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.MetadataReviewActionController.consignmentStatusUpdates
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
import services.Statuses.{
  ClosureMetadataType,
  CompletedValue,
  CompletedWithIssuesValue,
  DescriptiveMetadataType,
  DraftMetadataType,
  InProgressValue,
  IncompleteValue,
  MetadataReviewType
}
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

  val consignmentRef = "TDR-TEST-2024"
  val userEmail = "test@test.com"
  val expectedPath = s"/consignment/$consignmentId/metadata-review/review-progress"

  "MetadataReviewActionController GET" should {

    "render the correct metadata details page with an authenticated transfer advisor user" in {
      setGetConsignmentDetailsForMetadataReviewResponse()

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(isTransferAdvisor = true))
      val metadataReviewActionPage = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val metadataReviewActionPageAsString = contentAsString(metadataReviewActionPage)

      playStatus(metadataReviewActionPage) mustBe OK
      contentType(metadataReviewActionPage) mustBe Some("text/html")

      checkForExpectedMetadataReviewActionPageContent(metadataReviewActionPageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataReviewActionPageAsString, userType = "tna")
    }

    "render the correct metadata details page with an authenticated read only user" in {
      setGetConsignmentDetailsForMetadataReviewResponse()

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val metadataReviewActionPage = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val metadataReviewActionPageAsString = contentAsString(metadataReviewActionPage)

      playStatus(metadataReviewActionPage) mustBe OK
      contentType(metadataReviewActionPage) mustBe Some("text/html")

      checkForExpectedMetadataReviewActionPageContent(metadataReviewActionPageAsString)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataReviewActionPageAsString, userType = "tna")
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = instantiateMetadataReviewActionController(getUnauthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val metadataReviewActionPage = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      redirectLocation(metadataReviewActionPage).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(metadataReviewActionPage) mustBe FOUND
    }

    "return 403 if the review metadata action page is accessed by a non TNA user" in {
      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidKeycloakConfiguration)
      val response = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)

      status(response) mustBe FORBIDDEN
    }

    "Update the metadata review consignment status and send MetadataReviewSubmittedEvent message when a valid form is submitted with an accepted review and the api response is successful" in {
      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val status = CompletedValue.value

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

      wiremockServer.verify(
        postRequestedFor(urlEqualTo("/graphql"))
          .withRequestBody(containing("updateConsignmentStatus"))
          .withRequestBody(containing("MetadataReview"))
          .withRequestBody(containing("Completed"))
      )
    }

    "Update the consignment metadata review status, reset metadata statuses and send MetadataReviewSubmittedEvent message when a valid form is submitted with a rejected review and the api response is successful" in {
      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(isTransferAdvisor = true))
      val status = CompletedWithIssuesValue.value

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

      wiremockServer.getAllServeEvents.forEach { e =>
        println(e.getRequest.toString)
      }

      wiremockServer.verify(
        postRequestedFor(urlEqualTo("/graphql"))
          .withRequestBody(containing("updateConsignmentStatus"))
          .withRequestBody(containing("MetadataReview"))
          .withRequestBody(containing("CompletedWithIssues"))
      )

      wiremockServer.verify(
        postRequestedFor(urlEqualTo("/graphql"))
          .withRequestBody(containing("updateConsignmentStatus"))
          .withRequestBody(containing("DescriptiveMetadata"))
          .withRequestBody(containing("InProgress"))
      )

      wiremockServer.verify(
        postRequestedFor(urlEqualTo("/graphql"))
          .withRequestBody(containing("updateConsignmentStatus"))
          .withRequestBody(containing("ClosureMetadata"))
          .withRequestBody(containing("InProgress"))
      )

      wiremockServer.verify(
        postRequestedFor(urlEqualTo("/graphql"))
          .withRequestBody(containing("updateConsignmentStatus"))
          .withRequestBody(containing("DraftMetadata"))
          .withRequestBody(containing("InProgress"))
      )
    }

    "display errors when an invalid form is submitted" in {
      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(isTransferAdvisor = true))
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

  "consignmentStatusUpdates" should {
    "return correct updates when status is CompletedWithIssues" in {
      val formData = SelectedStatusData(CompletedWithIssuesValue.value)
      val result = consignmentStatusUpdates(formData)

      result mustBe Seq(
        (DescriptiveMetadataType.id, InProgressValue.value),
        (ClosureMetadataType.id, InProgressValue.value),
        (DraftMetadataType.id, InProgressValue.value),
        (MetadataReviewType.id, CompletedWithIssuesValue.value)
      )
    }

    "return only metadata review status update when status is Completed" in {
      val formData = SelectedStatusData(CompletedValue.value)
      val result = consignmentStatusUpdates(formData)

      result mustBe Seq(
        (MetadataReviewType.id, CompletedValue.value)
      )
    }
  }

  private def checkForExpectedMetadataReviewActionPageContent(pageAsString: String, isTransferAdvisor: Boolean = false): Unit = {
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
    if (isTransferAdvisor) {
      pageAsString must include("""2. Set the status of this review""")
      pageAsString must include(
        s"""<form action="/admin/metadata-review/$consignmentId?consignmentRef=TDR-2024-TEST&amp;userEmail=email%40test.com" method="POST" novalidate="">"""
      )
      pageAsString must include(s"""<option value="" selected>
                                   |                    Select a status
                                   |                </option>""".stripMargin)
      pageAsString must include(s"""<option value="Completed">Approve</option>""")
      pageAsString must include(s"""<option value="CompletedWithIssues">Reject</option>""")
    }
  }
}
