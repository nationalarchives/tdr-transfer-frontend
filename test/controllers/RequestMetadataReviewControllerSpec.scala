package controllers

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, times, verify}
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.Configuration
import play.api.Play.materializer
import play.api.http.Status.OK
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.MessagingService.MetadataReviewRequestEvent
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.util.UUID
import scala.concurrent.ExecutionContext

class RequestMetadataReviewControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val wiremockServer = new WireMockServer(9006)
  val messagingService: MessagingService = mock[MessagingService]
  val configuration: Configuration = mock[Configuration]

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements

  "requestMetadataReviewPage" should {

    "render the request metadata review page with an authenticated user" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val statuses = List(
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadata", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadataUpload", "Completed", someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = statuses)

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .requestMetadataReviewPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/request").withCSRFToken)

      val requestMetadataReviewPageAsString = contentAsString(content)

      playStatus(content) mustBe OK
      contentType(content) mustBe Some("text/html")
      requestMetadataReviewPageAsString must include("<title>Submit a metadata review - Transfer Digital Records - GOV.UK</title>")
      requestMetadataReviewPageAsString must include(s"""<a href="/consignment/$consignmentId/additional-metadata/download-metadata" class="govuk-back-link">Back</a>""")
      requestMetadataReviewPageAsString must include(s"""<form action="/consignment/$consignmentId/metadata-review/request" method="POST" novalidate="">""")
      requestMetadataReviewPageAsString must include(
        s"""<div class="govuk-button-group">
           |              <button data-prevent-double-click="true" class="govuk-button" type="submit" data-module="govuk-button" role="button">
           |                Submit metadata for review
           |              </button>
           |            </div>""".stripMargin
      )

      requestMetadataReviewPageAsString must include(
        s"""<a href="/consignment/$consignmentId/draft-metadata/prepare-metadata" class="govuk-link">Prepare your metadata</a>"""
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(requestMetadataReviewPageAsString, userType = "standard")

    }

    "return forbidden if the page is accessed by a judgment user" in {
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val page = controller
        .requestMetadataReviewPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/request"))

      playStatus(page) mustBe FORBIDDEN
    }

    "render an in-progress page when metadata review is already in progress" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val statuses = List(
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadata", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadataUpload", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "MetadataReview", "InProgress", someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = statuses)

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .requestMetadataReviewPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/request").withCSRFToken)

      val pageAsString = contentAsString(content)
      playStatus(content) mustBe OK
      pageAsString must include("<title>Your metadata is being reviewed - Transfer Digital Records - GOV.UK</title>")
      pageAsString must include("It is not possible to submit another metadata file.")
      pageAsString must include(s"""href="/consignment/$consignmentId/metadata-review/review-progress"""")
    }

    "redirect to draft metadata upload page when prerequisites are not complete" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      setConsignmentStatusResponse(app.configuration, wiremockServer)

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .requestMetadataReviewPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/request").withCSRFToken)

      playStatus(content) mustBe SEE_OTHER
      redirectLocation(content).value must equal(s"/consignment/$consignmentId/draft-metadata/upload")
    }

    "redirect to confirm transfer page when export exists" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val statuses = List(
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadata", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadataUpload", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "Export", "InProgress", someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = statuses)

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .requestMetadataReviewPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/request").withCSRFToken)

      playStatus(content) mustBe SEE_OTHER
      redirectLocation(content).value must equal(s"/consignment/$consignmentId/confirm-transfer")
    }

    "redirect to confirm transfer page when draft metadata was skipped" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val statuses = List(
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadata", "Skipped", someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = statuses)

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .requestMetadataReviewPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/request").withCSRFToken)

      playStatus(content) mustBe SEE_OTHER
      redirectLocation(content).value must equal(s"/consignment/$consignmentId/confirm-transfer")
    }
  }

  "submitMetadataForReview" should {

    "add status, send metadata review request notification and render the metadata review page" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      val statuses = List(
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadata", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadataUpload", "Completed", someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = statuses)
      setAddConsignmentStatusResponse(wiremockServer)
      setUpdateConsignmentStatus(wiremockServer)

      val seriesName = "SomeSeries".some
      val transferringBodyName = "SomeTransferringBody".some
      val totalClosedRecords = 1
      val totalFiles = 10
      val userId = UUID.fromString("c140d49c-93d0-4345-8d71-c97ff28b947e")
      val consignmentReference = "TDR-2024"

      setConsignmentsForMetadataReviewRequestResponse(
        wiremockServer,
        consignmentReference = consignmentReference,
        userId = userId,
        seriesName = seriesName,
        transferringBodyName = transferringBodyName,
        totalClosedRecords = totalClosedRecords,
        totalFiles = totalFiles
      )

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .submitMetadataForReview(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/metadata-review/submit-request"))

      playStatus(content) mustBe SEE_OTHER
      redirectLocation(content).get must equal(s"/consignment/$consignmentId/metadata-review/review-progress")

      val metadataReviewRequestEvent =
        MetadataReviewRequestEvent("intg", transferringBodyName, consignmentReference, consignmentId.toString, seriesName, userId.toString, "test@example.com", true, totalFiles)
      verify(messagingService, times(1)).sendMetadataReviewRequestNotification(metadataReviewRequestEvent)

    }

    "return forbidden if the page is accessed by a judgment user" in {
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val page = controller
        .submitMetadataForReview(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/submit-request"))

      playStatus(page) mustBe FORBIDDEN
    }

    "redirect to request page and do not submit when metadata review is already in progress" in {
      reset(messagingService)
      setConsignmentTypeResponse(wiremockServer, "standard")
      val statuses = List(
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadata", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadataUpload", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "MetadataReview", "InProgress", someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = statuses)

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .submitMetadataForReview(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/metadata-review/submit-request"))

      playStatus(content) mustBe SEE_OTHER
      redirectLocation(content).value must equal(s"/consignment/$consignmentId/metadata-review/request")
      verify(messagingService, times(0)).sendMetadataReviewRequestNotification(any[MetadataReviewRequestEvent])
    }

    "redirect to draft metadata upload page and do not submit when prerequisites are not complete" in {
      reset(messagingService)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentStatusResponse(app.configuration, wiremockServer)

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .submitMetadataForReview(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/metadata-review/submit-request"))

      playStatus(content) mustBe SEE_OTHER
      redirectLocation(content).value must equal(s"/consignment/$consignmentId/draft-metadata/upload")
      verify(messagingService, times(0)).sendMetadataReviewRequestNotification(any[MetadataReviewRequestEvent])
    }

    "redirect to confirm transfer page and do not submit when export exists" in {
      reset(messagingService)
      setConsignmentTypeResponse(wiremockServer, "standard")
      val statuses = List(
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadata", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadataUpload", "Completed", someDateTime, None),
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "Export", "InProgress", someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = statuses)

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .submitMetadataForReview(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/metadata-review/submit-request"))

      playStatus(content) mustBe SEE_OTHER
      redirectLocation(content).value must equal(s"/consignment/$consignmentId/confirm-transfer")
      verify(messagingService, times(0)).sendMetadataReviewRequestNotification(any[MetadataReviewRequestEvent])
    }

    "redirect to confirm transfer page and do not submit when draft metadata was skipped" in {
      reset(messagingService)
      setConsignmentTypeResponse(wiremockServer, "standard")
      val statuses = List(
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "DraftMetadata", "Skipped", someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = statuses)

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .submitMetadataForReview(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/metadata-review/submit-request"))

      playStatus(content) mustBe SEE_OTHER
      redirectLocation(content).value must equal(s"/consignment/$consignmentId/confirm-transfer")
      verify(messagingService, times(0)).sendMetadataReviewRequestNotification(any[MetadataReviewRequestEvent])
    }

    "return forbidden for a TNA user" in {
      setConsignmentTypeResponse(wiremockServer, "standard")

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val page = controller
        .submitMetadataForReview(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/submit-request"))

      playStatus(page) mustBe FORBIDDEN
    }
  }

  private def instantiateRequestMetadataReviewController(
      securityComponents: SecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration
  ) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    val config = new ApplicationConfig(app.configuration)
    new RequestMetadataReviewController(securityComponents, consignmentService, consignmentStatusService, keycloakConfiguration, config, messagingService)
  }
}
