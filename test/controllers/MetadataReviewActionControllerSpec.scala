package controllers

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.MetadataReviewActionController.consignmentStatusUpdates
import graphql.codegen.GetConsignmentDetailsForMetadataReview.getConsignmentDetailsForMetadataReview
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
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
import services.Statuses._
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.time.ZonedDateTime
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
  val downloadTemplateDomain: Option[String] = Some("MetadataReviewDetailTemplate")

  "MetadataReviewActionController GET" should {

    "render the correct metadata details page with an authenticated transfer advisor user" in {
      setGetConsignmentDetailsForMetadataReviewResponse()

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(isTransferAdvisor = true))
      val metadataReviewActionPage = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val metadataReviewActionPageAsString = contentAsString(metadataReviewActionPage)

      playStatus(metadataReviewActionPage) mustBe OK
      contentType(metadataReviewActionPage) mustBe Some("text/html")

      checkForExpectedMetadataReviewActionPageContent(metadataReviewActionPageAsString, templateDomain = downloadTemplateDomain)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataReviewActionPageAsString, userType = "tna")
    }

    "render the correct metadata details page with an authenticated read only user" in {
      setGetConsignmentDetailsForMetadataReviewResponse()

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val metadataReviewActionPage = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val metadataReviewActionPageAsString = contentAsString(metadataReviewActionPage)

      playStatus(metadataReviewActionPage) mustBe OK
      contentType(metadataReviewActionPage) mustBe Some("text/html")

      checkForExpectedMetadataReviewActionPageContent(metadataReviewActionPageAsString, templateDomain = downloadTemplateDomain)
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

      val seriesName = "SomeSeries".some
      val transferringBodyName = "SomeTransferringBody".some
      val totalClosedRecords = 1
      val totalFiles = 10

      val metadataReviewDecisionEventMatcher =
        new MetadataReviewSubmittedEventMatcher(consignmentRef, expectedPath, userEmail, status, seriesName, transferringBodyName, totalFiles)

      setConsignmentsForMetadataReviewRequestResponse(
        wiremockServer,
        consignmentReference = consignmentRef,
        userId = userId,
        seriesName = seriesName,
        transferringBodyName = transferringBodyName,
        totalClosedRecords = totalClosedRecords,
        totalFiles = totalFiles
      )
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

      val seriesName = "SomeSeries".some
      val transferringBodyName = "SomeTransferringBody".some
      val totalClosedRecords = 1
      val totalFiles = 10

      val metadataReviewDecisionEventMatcher =
        new MetadataReviewSubmittedEventMatcher(consignmentRef, expectedPath, userEmail, status, seriesName, transferringBodyName, totalFiles)

      setConsignmentsForMetadataReviewRequestResponse(
        wiremockServer,
        consignmentReference = consignmentRef,
        userId = userId,
        seriesName = seriesName,
        transferringBodyName = transferringBodyName,
        totalClosedRecords = totalClosedRecords,
        totalFiles = totalFiles
      )
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
          .withRequestBody(containing("DraftMetadata"))
          .withRequestBody(containing("InProgress"))
      )
    }

    "display errors when an invalid form is submitted" in {
      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(isTransferAdvisor = true))
      val consignmentRef = "TDR-TEST-2024"
      val userEmail = "test@test.com"
      val metadataReviewDecisionEvent =
        MetadataReviewSubmittedEvent(
          "intg",
          "consignmentRef",
          "urlLink",
          userEmail,
          "Approved",
          "transferringBodyName".some,
          "seriesCode".some,
          userId.toString,
          closedRecords = true,
          10
        )
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
      checkForExpectedMetadataReviewActionPageContent(metadataReviewSubmitAsString, templateDomain = downloadTemplateDomain)
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataReviewSubmitAsString, userType = "tna")
      verify(messagingService, times(0)).sendMetadataReviewSubmittedNotification(metadataReviewDecisionEvent)
    }
  }

  private def instantiateMetadataReviewActionController(
      securityComponents: SecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration,
      blockMetadataReviewV2: Boolean = true
  ) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    val config = getApplicationConfig(Map("featureAccessBlock.blockMetadataReviewV2" -> blockMetadataReviewV2))

    new MetadataReviewActionController(securityComponents, keycloakConfiguration, consignmentService, consignmentStatusService, messagingService, config)
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
              userId,
              totalClosedRecords = 0,
              includeTopLevelFolder = Some(false),
              totalFiles = 10,
              consignmentMetadata = List(
                getConsignmentDetailsForMetadataReview.GetConsignment.ConsignmentMetadata("LegalStatus", "Public Record(s)")
              )
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

  private def setGetConsignmentFilesMetadataResponse(logs: List[gcfm.GetConsignment.MetadataReviewLogs]) = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcfm.Data, gcfm.Variables]()
    val dataString = client
      .GraphqlData(Option(gcfm.Data(Option(gcfm.GetConsignment(files = List.empty, metadataReviewLogs = logs, consignmentReference = "TDR-2024-TEST")))))
      .asJson
      .printWith(Printer.noSpaces)
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentFilesMetadata"))
        .willReturn(okJson(dataString))
    )
  }

  "MetadataReviewActionController GET V2 (blockMetadataReviewV2 = false)" should {

    val submissionLog = gcfm.GetConsignment.MetadataReviewLogs(
      UUID.randomUUID(),
      consignmentId,
      userId,
      "Submission",
      ZonedDateTime.parse("2024-07-05T08:00:00Z")
    )
    val rejectionLog = gcfm.GetConsignment.MetadataReviewLogs(
      UUID.randomUUID(),
      consignmentId,
      UUID.randomUUID(),
      "Rejection",
      ZonedDateTime.parse("2024-07-10T10:30:00Z")
    )

    "render the V2 metadata details page for a TNA read-only user" in {
      setGetConsignmentDetailsForMetadataReviewResponse()
      setGetConsignmentFilesMetadataResponse(List(submissionLog))

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val pageAsString = contentAsString(page)

      playStatus(page) mustBe OK
      contentType(page) mustBe Some("text/html")
      pageAsString must include("Transfer details for TDR-2024-TEST")
    }

    "render the V2 metadata details page for a transfer advisor" in {
      setGetConsignmentDetailsForMetadataReviewResponse()
      setGetConsignmentFilesMetadataResponse(List(submissionLog))

      val controller =
        instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(isTransferAdvisor = true), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val pageAsString = contentAsString(page)

      playStatus(page) mustBe OK
      pageAsString must include("Transfer details for TDR-2024-TEST")
      pageAsString must include("""<button data-prevent-double-click="true" class="govuk-button" type="submit"""")
    }

    "hide the dropdown and notes input for a transfer advisor when action is not submission" in {
      setGetConsignmentDetailsForMetadataReviewResponse()
      setGetConsignmentFilesMetadataResponse(List(submissionLog, rejectionLog))

      val controller =
        instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(isTransferAdvisor = true), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val pageAsString = contentAsString(page)

      playStatus(page) mustBe OK
      pageAsString must not include """<button data-prevent-double-click="true" class="govuk-button" type="submit""""
      pageAsString must not include "Provide a reason for status change"
      pageAsString must not include """id="status-reason""""
    }

    "show status tag label 'Requested' for a Submission action" in {
      setGetConsignmentDetailsForMetadataReviewResponse()
      setGetConsignmentFilesMetadataResponse(List(submissionLog))

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val pageAsString = contentAsString(page)

      pageAsString must include("govuk-tag--red")
      pageAsString must include("Requested")
    }

    "show status tag label 'Rejected' for a Rejection action" in {
      setGetConsignmentDetailsForMetadataReviewResponse()
      setGetConsignmentFilesMetadataResponse(List(submissionLog, rejectionLog))

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val pageAsString = contentAsString(page)

      pageAsString must include("govuk-tag--yellow")
      pageAsString must include("Rejected")
    }

    "show the correct total submission count" in {
      setGetConsignmentDetailsForMetadataReviewResponse()
      setGetConsignmentFilesMetadataResponse(List(submissionLog, rejectionLog, submissionLog))

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val pageAsString = contentAsString(page)

      pageAsString must include("""<dd class="govuk-summary-list__value">
                        2
                    </dd>""")
    }

    "show the formatted date submitted from the last Submission log" in {
      setGetConsignmentDetailsForMetadataReviewResponse()
      setGetConsignmentFilesMetadataResponse(List(submissionLog))

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val pageAsString = contentAsString(page)

      pageAsString must include("5th July 2024, 08:00am")
    }

    "show Last reviewed by and Last updated rows when a non-Submission log exists" in {
      setGetConsignmentDetailsForMetadataReviewResponse()
      setGetConsignmentFilesMetadataResponse(List(submissionLog, rejectionLog))

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val pageAsString = contentAsString(page)

      pageAsString must include("Last reviewed by")
      pageAsString must include("""href="mailto:email@test.com"""")
      pageAsString must include("Last updated")
      pageAsString must include("10th July 2024, 10:30am")
    }

    "not show Last reviewed by and Last updated rows when only Submission logs exist" in {
      setGetConsignmentDetailsForMetadataReviewResponse()
      setGetConsignmentFilesMetadataResponse(List(submissionLog))

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val pageAsString = contentAsString(page)

      pageAsString must not include "Last reviewed by"
      pageAsString must not include "Last updated"
    }

    "not show Last reviewed by and Last updated rows when there are no review logs" in {
      setGetConsignmentDetailsForMetadataReviewResponse()
      setGetConsignmentFilesMetadataResponse(List.empty)

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val pageAsString = contentAsString(page)

      pageAsString must not include "Last reviewed by"
      pageAsString must not include "Last updated"
    }

    "not show 'View submission history' link when there is only 1 submission" in {
      setGetConsignmentDetailsForMetadataReviewResponse()
      setGetConsignmentFilesMetadataResponse(List(submissionLog))

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val pageAsString = contentAsString(page)

      pageAsString must not include "View submission history"
    }

    "show 'View submission history' link when there are more than 1 submissions" in {
      setGetConsignmentDetailsForMetadataReviewResponse()
      setGetConsignmentFilesMetadataResponse(List(submissionLog, rejectionLog, submissionLog))

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)
      val pageAsString = contentAsString(page)

      pageAsString must include("View submission history")
    }

    "show includeTopLevelFolder as 'Yes' when true" in {
      val client = new GraphQLConfiguration(app.configuration).getClient[getConsignmentDetailsForMetadataReview.Data, getConsignmentDetailsForMetadataReview.Variables]()
      val data = client
        .GraphqlData(
          Some(
            getConsignmentDetailsForMetadataReview.Data(
              Some(
                getConsignmentDetailsForMetadataReview.GetConsignment(
                  "TDR-2024-TEST",
                  Some("SeriesName"),
                  Some("TransferringBody"),
                  userId,
                  totalClosedRecords = 0,
                  includeTopLevelFolder = Some(true),
                  totalFiles = 5,
                  consignmentMetadata = List.empty
                )
              )
            )
          )
        )
        .asJson
        .printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql")).withRequestBody(containing("getConsignmentDetailsForMetadataReview")).willReturn(okJson(data)))
      setGetConsignmentFilesMetadataResponse(List(submissionLog))

      val controller = instantiateMetadataReviewActionController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), blockMetadataReviewV2 = false)
      val page = controller.consignmentMetadataDetails(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId").withCSRFToken)

      contentAsString(page) must include("Yes")
    }
  }

  "consignmentStatusUpdates" should {
    "return correct updates when status is CompletedWithIssues" in {
      val formData = SelectedStatusData(CompletedWithIssuesValue.value)
      val result = consignmentStatusUpdates(formData)

      result mustBe Seq(
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

  private def checkForExpectedMetadataReviewActionPageContent(pageAsString: String, isTransferAdvisor: Boolean = false, templateDomain: Option[String]): Unit = {
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
    pageAsString must include(downloadLinkHTML(consignmentId, templateDomain))
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

  class MetadataReviewSubmittedEventMatcher(
      expectedConsignmentRef: String,
      expectedPath: String,
      expectedEmail: String,
      expectedStatus: String,
      seriesName: Option[String],
      transferringBodyName: Option[String],
      totalFiles: Int
  ) extends ArgumentMatcher[MetadataReviewSubmittedEvent] {
    override def matches(event: MetadataReviewSubmittedEvent): Boolean = {
      event.environment == "intg" &&
      event.seriesCode == seriesName &&
      event.transferringBodyName == transferringBodyName &&
      event.consignmentReference == expectedConsignmentRef &&
      event.urlLink.contains(expectedPath) &&
      event.userEmail == expectedEmail &&
      event.status == expectedStatus &&
      event.closedRecords &&
      event.totalRecords == totalFiles
    }
  }
}
