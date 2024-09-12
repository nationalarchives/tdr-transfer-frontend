package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.{GetConsignment => gcs}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.Ignore
import org.scalatest.matchers.should.Matchers._
import play.api.Configuration
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.Statuses.{CompletedValue, CompletedWithIssuesValue, DraftMetadataType, FailedValue}
import services.{ConsignmentService, ConsignmentStatusService, DraftMetadataService, FileError}
import testUtils.FrontEndTestHelper

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class DraftMetadataChecksResultsControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val wiremockServer = new WireMockServer(9006)

  private val configuration: Configuration = mock[Configuration]

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "DraftMetadataChecksResultsController GET" should {
    "render page not found error when 'blockDraftMetadataUpload' set to 'true'" in {
      val controller = instantiateController()
      val additionalMetadataEntryMethodPage = controller.draftMetadataChecksResultsPage(consignmentId).apply(FakeRequest(GET, "/draft-metadata/checks-results").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")

      val pageAsString = contentAsString(additionalMetadataEntryMethodPage)

      playStatus(additionalMetadataEntryMethodPage) mustBe OK
      contentType(additionalMetadataEntryMethodPage) mustBe Some("text/html")
      pageAsString must include("<title>Page not found - Transfer Digital Records - GOV.UK</title>")
    }

    "return forbidden for a TNA user" in {
      val controller = instantiateController(blockDraftMetadataUpload = false, keycloakConfiguration = getValidTNAUserKeycloakConfiguration())
      val additionalMetadataEntryMethodPage = controller.draftMetadataChecksResultsPage(consignmentId).apply(FakeRequest(GET, "/draft-metadata/checks-results").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")

      playStatus(additionalMetadataEntryMethodPage) mustBe FORBIDDEN
    }
  }

  "DraftMetadataChecksResultsController should render the page with the correct status" should {
    val draftMetadataStatuses = Table(
      ("status", "progress"),
      (CompletedValue.value, DraftMetadataProgress("IMPORTED", "blue")),
      (FailedValue.value, DraftMetadataProgress("FAILED", "red"))
    )
    forAll(draftMetadataStatuses) { (statusValue, progress) =>
      s"render the draftMetadataResults page when the status is $statusValue" in {
        val controller = instantiateController(blockDraftMetadataUpload = false)
        val additionalMetadataEntryMethodPage = controller
          .draftMetadataChecksResultsPage(consignmentId)
          .apply(FakeRequest(GET, "/draft-metadata/checks-results").withCSRFToken)
        setConsignmentTypeResponse(wiremockServer, "standard")
        setConsignmentReferenceResponse(wiremockServer)
        val someDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())
        val consignmentStatuses = List(
          gcs.ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), DraftMetadataType.id, statusValue, someDateTime, None)
        )
        setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)

        val pageAsString = contentAsString(additionalMetadataEntryMethodPage)

        playStatus(additionalMetadataEntryMethodPage) mustBe OK
        contentType(additionalMetadataEntryMethodPage) mustBe Some("text/html")
        pageAsString must include("<title>Results of CSV Checks - Transfer Digital Records - GOV.UK</title>")
        pageAsString must include(
          """            <h1 class="govuk-heading-l">
            |                Results of your metadata checks
            |            </h1>""".stripMargin
        )
        pageAsString must include(
          s"""          <dl class="govuk-summary-list">
            |                <div class="govuk-summary-list__row">
            |                    <dt class="govuk-summary-list__key">
            |                        Status
            |                    </dt>
            |                    <dd class="govuk-summary-list__value">
            |                        <strong class="govuk-tag govuk-tag--${progress.colour}">
            |                            ${progress.value}
            |                        </strong>
            |                    </dd>
            |                </div>
            |            </dl>""".stripMargin
        )
        pageAsString must include(
          s"""            <div class="govuk-button-group">
             |                <a href="/consignment/$consignmentId/additional-metadata/download-metadata" role="button" draggable="false" class="govuk-button" data-module="govuk-button">
             |                    Next
             |                </a>
             |            </div>""".stripMargin
        )
      }
    }
  }

  "DraftMetadataChecksResultsController should render the error page with error report download button" should {
    val draftMetadataStatuses = Table(
      ("status", "progress", "fileError"),
      (CompletedWithIssuesValue.value, DraftMetadataProgress("ERRORS", "red"), FileError.SCHEMA_VALIDATION)
    )
    forAll(draftMetadataStatuses) { (statusValue, progress, fileError) =>
      s"render the draftMetadataResults page when the status is $statusValue" in {
        val controller = instantiateController(blockDraftMetadataUpload = false, fileError = fileError)
        val additionalMetadataEntryMethodPage = controller
          .draftMetadataChecksResultsPage(consignmentId)
          .apply(FakeRequest(GET, "/draft-metadata/checks-results").withCSRFToken)
        setConsignmentTypeResponse(wiremockServer, "standard")
        setConsignmentReferenceResponse(wiremockServer)
        val someDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())
        val consignmentStatuses = List(
          gcs.ConsignmentStatuses(UUID.randomUUID(), UUID.randomUUID(), DraftMetadataType.id, statusValue, someDateTime, None)
        )
        setConsignmentStatusResponse(app.configuration, wiremockServer, consignmentStatuses = consignmentStatuses)

        val pageAsString = contentAsString(additionalMetadataEntryMethodPage)

        playStatus(additionalMetadataEntryMethodPage) mustBe OK
        contentType(additionalMetadataEntryMethodPage) mustBe Some("text/html")
        pageAsString must include("<title>Results of CSV Checks - Transfer Digital Records - GOV.UK</title>")
        pageAsString.stripMargin must include(
          """<h1 class="govuk-heading-l">
            |    Results of your metadata checks
            |</h1>""".stripMargin
        )
        pageAsString must include(
          s"""<dl class="govuk-summary-list">
             |    <div class="govuk-summary-list__row">
             |        <dt class="govuk-summary-list__key">
             |            Status
             |        </dt>
             |        <dd class="govuk-summary-list__value">
             |            <strong class="govuk-tag govuk-tag--orange">Issues found</strong>
             |        </dd>
             |    </div>
             |
             |    <div class="govuk-summary-list__row">
             |        <dt class="govuk-summary-list__key">
             |            Details
             |        </dt>
             |        <dd class="govuk-summary-list__value">
             |            Require details message for draftMetadata.validation.details.${fileError}
             |        </dd>
             |    </div>
             |
             |    <div class="govuk-summary-list__row">
             |        <dt class="govuk-summary-list__key">
             |            Action
             |        </dt>
             |        <dd class="govuk-summary-list__value">
             |            Require action message for draftMetadata.validation.action.${fileError}
             |        </dd>
             |    </div>
             |</dl>""".stripMargin
        )
        pageAsString must include(
          s"""<p class="govuk-body">The report below contains details about issues found.</p>
             |
             |            <button class="govuk-button govuk-button--secondary govuk-!-margin-bottom-8" data-module="govuk-button">
             |                <span aria-hidden="true" class="tna-button-icon tna-button-icon--download">
             |                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 23 23">
             |                        <path fill="#020202" d="m11.5 16.75-6.563-6.563 1.838-1.903 3.412 3.413V1h2.626v10.697l3.412-3.413 1.837 1.903L11.5 16.75ZM3.625 22c-.722 0-1.34-.257-1.853-.77A2.533 2.533 0 0 1 1 19.375v-3.938h2.625v3.938h15.75v-3.938H22v3.938c0 .722-.257 1.34-.77 1.855a2.522 2.522 0 0 1-1.855.77H3.625Z"/>
             |                    </svg>
             |                </span>
             |                Download report
             |            </button>""".stripMargin
        )
      }
    }
  }

  private def instantiateController(
      securityComponents: SecurityComponents = getAuthorisedSecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration,
      blockDraftMetadataUpload: Boolean = true,
      fileError: FileError.FileError = FileError.UNSPECIFIED
  ): DraftMetadataChecksResultsController = {
    when(configuration.get[Boolean]("featureAccessBlock.blockDraftMetadataUpload")).thenReturn(blockDraftMetadataUpload)
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    val draftMetaDataService = mock[DraftMetadataService]
    when(draftMetaDataService.getErrorType(any[UUID])).thenReturn(Future.successful(fileError))

    new DraftMetadataChecksResultsController(securityComponents, keycloakConfiguration, consignmentService, applicationConfig, consignmentStatusService, draftMetaDataService)
  }
}
