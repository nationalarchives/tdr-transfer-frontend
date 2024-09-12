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
    when(draftMetaDataService.getErrorType(any[UUID])).thenReturn(Future.successful(FileError.UNSPECIFIED))

    new DraftMetadataChecksResultsController(securityComponents, keycloakConfiguration, consignmentService, applicationConfig, consignmentStatusService, draftMetaDataService)
  }
}
