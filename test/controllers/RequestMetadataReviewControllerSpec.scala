package controllers

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import org.mockito.Mockito.{times, verify, when}
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.Configuration
import play.api.http.Status.OK
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.MessagingService.MetadataReviewRequestEvent
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
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

  val someDateTime: ZonedDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())

  "requestMetadataReviewPage" should {

    "render the request metadata review page with an authenticated user when 'blockMetadataReview' set to 'false'" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .requestMetadataReviewPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/request"))

      val requestMetadataReviewPageAsString = contentAsString(content)

      playStatus(content) mustBe OK
      contentType(content) mustBe Some("text/html")
      requestMetadataReviewPageAsString must include("<title>Request a metadata review - Transfer Digital Records - GOV.UK</title>")
      requestMetadataReviewPageAsString must include(s"""<a href="/consignment/$consignmentId/additional-metadata/download-metadata" class="govuk-back-link">Back</a>""")
      requestMetadataReviewPageAsString must include(
        s"""
          |          <div class="govuk-button-group">
          |            <a href="/consignment/$consignmentId/metadata-review/submit-request" role="button" draggable="false" class="govuk-button" data-module="govuk-button">
          |              Submit metadata for review
          |            </a>
          |          </div>
          |""".stripMargin
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(requestMetadataReviewPageAsString, userType = "standard")

    }

    "render page not found error when 'blockMetadataReview' set to 'true'" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration, blockMetadataReview = true)
      val content = controller
        .requestMetadataReviewPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/request"))

      val requestMetadataReviewPageAsString = contentAsString(content)

      playStatus(content) mustBe OK
      contentType(content) mustBe Some("text/html")
      requestMetadataReviewPageAsString must include("<title>Page not found - Transfer Digital Records - GOV.UK</title>")
    }

    "return forbidden if the page is accessed by a judgment user" in {
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val page = controller
        .requestMetadataReviewPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/request"))

      playStatus(page) mustBe FORBIDDEN
    }
  }

  "submitMetadataForReview" should {

    "add status, send metadata review request notification and render the metadata review page" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentSummaryResponse(wiremockServer, transferringBodyName = "Mock".some, consignmentReference = "TDR-2024")
      setAddConsignmentStatusResponse(wiremockServer)

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .submitMetadataForReview(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/submit-request"))

      val requestMetadataReviewPageAsString = contentAsString(content)

      val metadataReviewRequestEvent = MetadataReviewRequestEvent("Mock".some, "TDR-2024", consignmentId.toString, "c140d49c-93d0-4345-8d71-c97ff28b947e", "test@example.com")
      verify(messagingService, times(1)).sendMetadataReviewRequestNotification(metadataReviewRequestEvent)

      playStatus(content) mustBe OK
      contentType(content) mustBe Some("text/html")
      requestMetadataReviewPageAsString must include("<title>Metadata review - Transfer Digital Records - GOV.UK</title>")
    }

    "return forbidden if the page is accessed by a judgment user" in {
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val controller = instantiateRequestMetadataReviewController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val page = controller
        .submitMetadataForReview(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/submit-request"))

      playStatus(page) mustBe FORBIDDEN
    }
  }

  private def instantiateRequestMetadataReviewController(
      securityComponents: SecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration,
      blockMetadataReview: Boolean = false
  ) = {
    when(configuration.get[Boolean]("featureAccessBlock.blockMetadataReview")).thenReturn(blockMetadataReview)
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    new RequestMetadataReviewController(securityComponents, consignmentService, consignmentStatusService, keycloakConfiguration, applicationConfig, messagingService)
  }
}
