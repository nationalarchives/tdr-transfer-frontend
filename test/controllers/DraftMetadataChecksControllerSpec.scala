package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax.EncoderOps
import org.mockito.Mockito.when
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.TableDrivenPropertyChecks
import play.api.Configuration
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.{ConsignmentService, ConsignmentStatusService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.ExecutionContext

class DraftMetadataChecksControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)
  val checkPageForStaticElements = new CheckPageForStaticElements
  val consignmentId: UUID = UUID.fromString("b5bbe4d6-01a7-4305-99ef-9fce4a67917a")
  val zoneId = ZoneId.systemDefault()

  private val configuration: Configuration = mock[Configuration]
  private val expectedTitle: String = "<title>Checking your metadata - Transfer Digital Records - GOV.UK</title>"
  private val expectedHeading: String = """<h1 class="govuk-heading-l">Checking your metadata</h1>"""
  private val expectedInstruction: String = """<p class="govuk-body">Please wait while we check your metadata against the uploaded records. This may take a few minutes.</p>"""
  private val expectedInput: String = s"""<input id="consignmentId" type="hidden" value="${consignmentId}">"""

  val expectedNotificationBanner =
    """
      |                    <div class="govuk-notification-banner__header">
      |                        <h2 class="govuk-notification-banner__title" id="govuk-notification-banner-title">
      |                        Important
      |                        </h2>
      |                    </div>
      |                    <div class="govuk-notification-banner__content">
      |                        <h3 class="govuk-notification-banner__heading">Your metadata has been checked.</h3>
      |                        <p class="govuk-body">Please click 'Continue' to see your results.</p>
      |                    </div>
      |""".stripMargin

  val expectedFormAction =
    """
      |                <form action="/consignment/b5bbe4d6-01a7-4305-99ef-9fce4a67917a/draft-metadata/checks-results">
      |                    <button type="submit" role="button" draggable="false" id="draft-metadata-checks-continue" class="govuk-button govuk-button--disabled" data-tdr-module="button-disabled" data-module="govuk-button" aria-disabled="true" aria-describedby="reason-disabled">
      |                Continue
      |                    </button>
      |                    <p class="govuk-visually-hidden" id="reason-disabled">
      |                    This button will be enabled when we have finished checking your metadata.
      |                    </p>
      |                </form>
      |""".stripMargin

  private val expectedResponse =
    s"""[{"consignmentStatusId":"f3d9bab1-ac65-441b-8516-81a1590ed98e","consignmentId":"b5bbe4d6-01a7-4305-99ef-9fce4a67917a","statusType":"DraftMetadata","value":"Completed","createdDatetime":"2022-03-10T01:00:00Z[${zoneId.toString}]","modifiedDatetime":null}]""".stripMargin

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "DraftMetadataChecksController POST 'draft-metadata/validation-progress'" should {
    "return valid status response" in {
      val inProgressDataAsString = progressData("Completed")
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      mockMetadataValidationProgress(inProgressDataAsString)

      val controller = instantiateController()

      val response = controller
        .draftMetadataValidationProgress(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/draft-metadata/validation-progress").withCSRFToken)
      val responseAsString = contentAsString(response)
      responseAsString must equal(expectedResponse)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = instantiateController(securityComponents = getUnauthorisedSecurityComponents)
      val page = controller
        .draftMetadataValidationProgress(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/draft-metadata/validation-progress").withCSRFToken)

      playStatus(page) mustBe SEE_OTHER
      redirectLocation(page).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "render the error page if the token is invalid" in {
      val controller = instantiateController(keycloakConfiguration = getInvalidKeycloakConfiguration)
      val page = controller
        .draftMetadataValidationProgress(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/draft-metadata/validation-progress").withCSRFToken)

      val failure: Throwable = page.failed.futureValue
      failure mustBe an[RuntimeException]
    }

    "return forbidden if the pages are accessed by a judgment user" in {
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      val controller = instantiateController(keycloakConfiguration = getValidJudgmentUserKeycloakConfiguration)
      val page = controller
        .draftMetadataValidationProgress(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/draft-metadata/validation-progress").withCSRFToken)
      playStatus(page) mustBe FORBIDDEN
    }

    "return forbidden for a TNA user" in {
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      val controller = instantiateController(keycloakConfiguration = getValidTNAUserKeycloakConfiguration)
      val page = controller
        .draftMetadataValidationProgress(consignmentId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/draft-metadata/validation-progress").withCSRFToken)
      playStatus(page) mustBe FORBIDDEN
    }
  }

  "DraftMetadataChecksController GET '/draft-metadata/checks'" should {
    "render page not found error when 'blockDraftMetadataUpload' set to 'true'" in {
      val controller = instantiateController(blockDraftMetadataUpload = true)
      val page = controller.draftMetadataChecksPage(consignmentId).apply(FakeRequest(GET, "/draft-metadata/checks").withCSRFToken)
      setConsignmentTypeResponse(wiremockServer, "standard")

      val pageAsString = contentAsString(page)

      playStatus(page) mustBe OK
      contentType(page) mustBe Some("text/html")
      pageAsString must include("<title>Page not found - Transfer Digital Records - GOV.UK</title>")
    }

    "render 'draft metadata checks' page correctly'" in {
      val inProgressDataAsString = progressData("Completed")

      val controller = instantiateController()

      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentReferenceResponse(wiremockServer)
      mockMetadataValidationProgress(inProgressDataAsString)

      val page = controller
        .draftMetadataChecksPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/draft-metadata/checks").withCSRFToken)

      val pageAsString: String = contentAsString(page)

      playStatus(page) mustBe OK
      contentType(page) mustBe Some("text/html")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = "Standard")
      pageAsString must include(expectedTitle)
      pageAsString must include(expectedHeading)
      pageAsString must include(expectedInstruction)
      pageAsString must include(expectedInput)
      pageAsString must include(expectedNotificationBanner)
      pageAsString must include(expectedFormAction)
    }

    "return a redirect to the auth server with an unauthenticated user" in {
      val controller = instantiateController(securityComponents = getUnauthorisedSecurityComponents)
      val page = controller.draftMetadataChecksPage(consignmentId).apply(FakeRequest(GET, s"/consignment/$consignmentId/draft-metadata/checks"))

      playStatus(page) mustBe FOUND
      redirectLocation(page).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "render the error page if the token is invalid" in {
      val controller = instantiateController(keycloakConfiguration = getInvalidKeycloakConfiguration)
      val page = controller.draftMetadataChecksPage(consignmentId).apply(FakeRequest(GET, s"/consignment/$consignmentId/draft-metadata/checks"))

      val failure = page.failed.futureValue

      failure.getMessage should include("Token not provided")
    }

    "return forbidden if the pages are accessed by a judgment user" in {
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      val controller = instantiateController(keycloakConfiguration = getValidJudgmentUserKeycloakConfiguration)
      val page = controller.draftMetadataChecksPage(consignmentId).apply(FakeRequest(GET, s"/consignment/$consignmentId/draft-metadata/checks"))
      playStatus(page) mustBe FORBIDDEN
    }
  }

  private def instantiateController(
      securityComponents: SecurityComponents = getAuthorisedSecurityComponents,
      keycloakConfiguration: KeycloakConfiguration = getValidStandardUserKeycloakConfiguration,
      blockDraftMetadataUpload: Boolean = false
  ): DraftMetadataChecksController = {
    when(configuration.get[Boolean]("featureAccessBlock.blockDraftMetadataUpload")).thenReturn(blockDraftMetadataUpload)
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

    new DraftMetadataChecksController(securityComponents, keycloakConfiguration, frontEndInfoConfiguration, consignmentService, consignmentStatusService, applicationConfig)
  }

  private def progressData(draftMetadataStatusValue: String, consignmentId: UUID = consignmentId): String = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcs.Data, gcs.Variables]()
    val someDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), zoneId)

    val draftMetadataStatus: ConsignmentStatuses = ConsignmentStatuses(
      UUID.fromString("f3d9bab1-ac65-441b-8516-81a1590ed98e"),
      consignmentId,
      "DraftMetadata",
      draftMetadataStatusValue,
      someDateTime,
      None
    )

    val statuses: List[ConsignmentStatuses] = List(draftMetadataStatus)

    val data: client.GraphqlData = client.GraphqlData(
      Some(
        gcs.Data(
          Some(
            gcs.GetConsignment(None, None, statuses)
          )
        )
      )
    )
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    dataString
  }

  private def mockMetadataValidationProgress(dataString: String): StubMapping = {
    setConsignmentTypeResponse(wiremockServer, "standard")
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentStatus"))
        .willReturn(okJson(dataString))
    )
  }
}
