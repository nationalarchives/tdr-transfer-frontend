package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import play.api.Configuration
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status => playStatus, _}
import services.{ConsignmentService, ConsignmentStatusService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext

class MetadataReviewStatusControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)
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

  "metadataReviewStatusPage GET" should {
    "render the default metadata review status page with an authenticated user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val statuses = List(
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "MetadataReview", "InProgress", someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = statuses)

      val controller = instantiateMetadataReviewStatusController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .metadataReviewStatusPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/review-progress"))

      val metadataReviewStatusPageAsString = contentAsString(content)

      playStatus(content) mustBe OK
      contentType(content) mustBe Some("text/html")

      metadataReviewStatusPageAsString must include("<title>Metadata review - Transfer Digital Records - GOV.UK</title>")
      metadataReviewStatusPageAsString must include("""<h1 class="govuk-heading-xl">Metadata review</h1>""")

      metadataReviewStatusPageAsString must include(
        """<div class="da-alert da-alert--with-icon">
          |    <div class="da-alert__icon">
          |        <div aria-hidden="true"><svg class="da-icon da-icon--xl" """.stripMargin
      )

      metadataReviewStatusPageAsString must include(
        s"""</div>
           |    <div class="da-alert__content">
           |        <h2 class="da-alert__heading">Your review is in progress</h2>
           |        <p>When the review is complete you will receive an email to <strong>test@example.com</strong> with further instructions.</p>
           |    </div>
           |</div>""".stripMargin
      )

      metadataReviewStatusPageAsString must include(
        s"""<p class="govuk-body">
           |    You can leave and return to this page at any time from the <a class="govuk-notification-banner__link" href="/view-transfers">
           |    View transfers</a> page.</p>""".stripMargin
      )

      metadataReviewStatusPageAsString must include(downloadLinkHTML(consignmentId))

      metadataReviewStatusPageAsString must include(
        s"""<p class="govuk-body">
           |    If you have any queries email <a href="mailto:nationalArchives.email">nationalArchives.email</a>
           |    quoting the consignment reference: TEST-TDR-2021-GB</p>""".stripMargin
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataReviewStatusPageAsString, userType = "standard")

    }

    "render the failed review status page with an authenticated user metadataReview status is 'CompletedWithIssues'" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val statuses = List(
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "MetadataReview", "CompletedWithIssues", someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = statuses)

      val controller = instantiateMetadataReviewStatusController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .metadataReviewStatusPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/review-progress").withCSRFToken)

      val metadataReviewStatusPageAsString = contentAsString(content)

      playStatus(content) mustBe OK
      contentType(content) mustBe Some("text/html")

      metadataReviewStatusPageAsString must include("<title>Metadata review - Transfer Digital Records - GOV.UK</title>")
      metadataReviewStatusPageAsString must include("""<h1 class="govuk-heading-xl">Metadata review</h1>""")

      metadataReviewStatusPageAsString must include(
        """<div class="da-alert da-alert--error da-alert--with-icon">
          |    <div class="da-alert__icon">
          |        <div aria-hidden="true"><svg class="da-icon da-icon--xxl" """.stripMargin
      )

      metadataReviewStatusPageAsString must include(
        s"""</div>
           |    <div class="da-alert__content">
           |        <h2 class="da-alert__heading">We found issues in your metadata</h2>
           |        <p>We will email guidance to <strong>test@example.com</strong></p>
           |    </div>
           |</div>""".stripMargin
      )

      metadataReviewStatusPageAsString must include(
        s"""<p class="govuk-body">Follow the guidance to amend your metadata. You can download a copy of the metadata we reviewed below.</p>""".stripMargin
      )

      metadataReviewStatusPageAsString must include(downloadLinkHTML(consignmentId))
      metadataReviewStatusPageAsString must include(
        s"""
           |<div class="govuk-button-group">
           |    <a class="govuk-button" href="/consignment/$consignmentId/draft-metadata/upload" role="button" draggable="false" data-module="govuk-button">
           |        Continue
           |    </a>
           |</div>""".stripMargin
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataReviewStatusPageAsString, userType = "standard")

    }

    "render the review success status page with an authenticated user metadataReview status is 'Completed'" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val statuses = List(
        ConsignmentStatuses(UUID.randomUUID(), consignmentId, "MetadataReview", "Completed", someDateTime, None)
      )
      setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = statuses)

      val controller = instantiateMetadataReviewStatusController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .metadataReviewStatusPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/review-progress"))

      val metadataReviewStatusPageAsString = contentAsString(content)

      playStatus(content) mustBe OK
      contentType(content) mustBe Some("text/html")

      metadataReviewStatusPageAsString must include("<title>Metadata review - Transfer Digital Records - GOV.UK</title>")
      metadataReviewStatusPageAsString must include("""<h1 class="govuk-heading-xl">Metadata review</h1>""")

      metadataReviewStatusPageAsString must include(
        """<div class="da-alert da-alert--success da-alert--with-icon">
          |    <div class="da-alert__icon">
          |        <div aria-hidden="true"><svg class="da-icon da-icon--xl" """.stripMargin
      )

      metadataReviewStatusPageAsString must include(
        s"""</div>
           |    <div class="da-alert__content">
           |        <h2 class="da-alert__heading">You can now complete your transfer</h2>
           |        <p>
           |            The metadata you submitted has been reviewed and no issues were found.
           |        </p>
           |    </div>
           |</div>""".stripMargin
      )

      metadataReviewStatusPageAsString must include(
        s"""<p class="govuk-body">
           |    You can now continue to confirm your transfer.
           |</p>
           |
           |<div class="govuk-button-group">
           |    <a class="govuk-button" href="/consignment/$consignmentId/confirm-transfer" role="button" draggable="false" data-module="govuk-button">
           |        Continue
           |    </a>
           |</div>""".stripMargin
      )

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(metadataReviewStatusPageAsString, userType = "standard")

    }

    "render page not found error when metadata review status is missing" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      setConsignmentStatusResponse(app.configuration, wiremockServer)

      val controller = instantiateMetadataReviewStatusController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val content = controller
        .metadataReviewStatusPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/review-progress"))

      val requestMetadataReviewPageAsString = contentAsString(content)

      playStatus(content) mustBe OK
      contentType(content) mustBe Some("text/html")
      requestMetadataReviewPageAsString must include("<title>Page not found - Transfer Digital Records - GOV.UK</title>")
    }

    "return forbidden if the page is accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      setConsignmentStatusResponse(app.configuration, wiremockServer)

      val controller = instantiateMetadataReviewStatusController(getAuthorisedSecurityComponents, getValidStandardUserKeycloakConfiguration)
      val page = controller
        .metadataReviewStatusPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/review-progress"))

      playStatus(page) mustBe FORBIDDEN
    }

    "return forbidden for a TNA user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      setConsignmentStatusResponse(app.configuration, wiremockServer)

      val controller = instantiateMetadataReviewStatusController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val page = controller
        .metadataReviewStatusPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/metadata-review/review-progress"))

      playStatus(page) mustBe FORBIDDEN
    }
  }

  private def instantiateMetadataReviewStatusController(
      securityComponents: SecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration
  ) = {
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    new MetadataReviewStatusController(securityComponents, consignmentService, consignmentStatusService, keycloakConfiguration, applicationConfig)
  }
}
