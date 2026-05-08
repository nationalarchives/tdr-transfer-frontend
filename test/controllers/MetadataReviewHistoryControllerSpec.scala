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
import play.api.test.Helpers.{status => playStatus, _}
import services.{ConsignmentService, ConsignmentStatusService, MessagingService}
import testUtils.FrontEndTestHelper
import uk.gov.nationalarchives.tdr.common.utils.statuses.MetadataReviewLogAction.{Approval, Rejection, Submission}

import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext

class MetadataReviewHistoryControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentId: UUID = UUID.randomUUID()
  val userId: UUID = UUID.randomUUID()
  val reviewerId: UUID = UUID.randomUUID()

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val submissionLog: getConsignmentDetailsForMetadataReview.GetConsignment.MetadataReviewLogs =
    getConsignmentDetailsForMetadataReview.GetConsignment.MetadataReviewLogs(
      UUID.randomUUID(),
      consignmentId,
      userId,
      Submission.value,
      ZonedDateTime.parse("2024-07-05T07:00:00Z"),
      None
    )

  val rejectionLog: getConsignmentDetailsForMetadataReview.GetConsignment.MetadataReviewLogs =
    getConsignmentDetailsForMetadataReview.GetConsignment.MetadataReviewLogs(
      UUID.randomUUID(),
      consignmentId,
      reviewerId,
      Rejection.value,
      ZonedDateTime.parse("2024-07-06T09:30:00Z"),
      Some("Metadata fields are incomplete")
    )

  val approvalLog: getConsignmentDetailsForMetadataReview.GetConsignment.MetadataReviewLogs =
    getConsignmentDetailsForMetadataReview.GetConsignment.MetadataReviewLogs(
      UUID.randomUUID(),
      consignmentId,
      reviewerId,
      Approval.value,
      ZonedDateTime.parse("2024-07-10T10:15:00Z"),
      None
    )

  "MetadataReviewHistoryController GET" should {

    "return 404 when blockMetadataReviewV2 is true" in {
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration(), blockMetadataReviewV2 = true)
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)

      playStatus(result) mustBe NOT_FOUND
    }

    "redirect to login with an unauthenticated user" in {
      val controller = instantiateController(getUnauthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)

      redirectLocation(result).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
      playStatus(result) mustBe FOUND
    }

    "return 403 when accessed by a non-TNA user" in {
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidKeycloakConfiguration)
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)

      playStatus(result) mustBe FORBIDDEN
    }

    "render the history page with the consignment reference in the heading" in {
      setGraphQLResponse(List(submissionLog))
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)

      playStatus(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      contentAsString(result) must include("Submission history for TDR-2024-TEST")
    }

    "render the department, series and record count in the summary list" in {
      setGraphQLResponse(List(submissionLog))
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)
      val pageAsString = contentAsString(result)

      pageAsString must include("TransferringBody")
      pageAsString must include("SeriesName")
      pageAsString must include("Total: 10")
    }

    "show one history row for a single submission with no review" in {
      setGraphQLResponse(List(submissionLog))
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)
      val pageAsString = contentAsString(result)

      pageAsString must include("""<td class="govuk-table__cell">1</td>""")
      pageAsString must include("5th July 2024")
      pageAsString must include("govuk-tag--red")
      pageAsString must include("Requested")
    }

    "show the formatted date submitted" in {
      setGraphQLResponse(List(submissionLog))
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)

      contentAsString(result) must include("5th July 2024, 08:00am")
    }

    "show Rejected status tag for a rejection review" in {
      setGraphQLResponse(List(submissionLog, rejectionLog))
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)
      val pageAsString = contentAsString(result)

      pageAsString must include("govuk-tag--yellow")
      pageAsString must include("Rejected")
    }

    "show Approved status tag for an approval review" in {
      setGraphQLResponse(List(submissionLog, approvalLog))
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)
      val pageAsString = contentAsString(result)

      pageAsString must include("govuk-tag--green")
      pageAsString must include("Approved")
    }

    "show the reviewer name and email as a mailto link" in {
      setGraphQLResponse(List(submissionLog, rejectionLog))
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)
      val pageAsString = contentAsString(result)

      pageAsString must include("firstName lastName")
      pageAsString must include("""href="mailto:email@test.com"""")
    }

    "show the note in the reason for status change column" in {
      setGraphQLResponse(List(submissionLog, rejectionLog))
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)

      contentAsString(result) must include("Metadata fields are incomplete")
    }

    "show the formatted date reviewed" in {
      setGraphQLResponse(List(submissionLog, rejectionLog))
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)

      contentAsString(result) must include("6th July 2024, 10:30am")
    }

    "show multiple history rows in reverse chronological order (most recent submission first)" in {
      val secondSubmission = submissionLog.copy(
        metadataReviewLogId = UUID.randomUUID(),
        eventTime = ZonedDateTime.parse("2024-07-08T09:00:00Z")
      )
      setGraphQLResponse(List(submissionLog, rejectionLog, secondSubmission, approvalLog))
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)
      val pageAsString = contentAsString(result)

      pageAsString must include("""<td class="govuk-table__cell">1</td>""")
      pageAsString must include("""<td class="govuk-table__cell">2</td>""")

      val row2Pos = pageAsString.indexOf(">2<")
      val row1Pos = pageAsString.indexOf(">1<")
      row2Pos should be < row1Pos
    }

    "show an empty reason cell when the review has no note" in {
      setGraphQLResponse(List(submissionLog, approvalLog))
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)
      val pageAsString = contentAsString(result)

      pageAsString must include("""<span class="govuk-body-s"></span>""")
    }

    "include a back link to the transfer details page" in {
      setGraphQLResponse(List(submissionLog))
      val controller = instantiateController(getAuthorisedSecurityComponents, getValidTNAUserKeycloakConfiguration())
      val result = controller.getConsignmentMetadataHistory(consignmentId).apply(FakeRequest(GET, s"/admin/metadata-review/$consignmentId/history").withCSRFToken)

      contentAsString(result) must include(s"/admin/metadata-review/$consignmentId")
      contentAsString(result) must include("Back to transfer details")
    }
  }

  private def instantiateController(
      securityComponents: SecurityComponents,
      keycloakConfiguration: KeycloakConfiguration,
      blockMetadataReviewV2: Boolean = false
  ): MetadataReviewHistoryController = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    val messagingService = mock[MessagingService]
    val config = getApplicationConfig(Map("featureAccessBlock.blockMetadataReviewV2" -> blockMetadataReviewV2))
    new MetadataReviewHistoryController(securityComponents, keycloakConfiguration, consignmentService, consignmentStatusService, messagingService, config)
  }

  private def setGraphQLResponse(logs: List[getConsignmentDetailsForMetadataReview.GetConsignment.MetadataReviewLogs]): Unit = {
    val client = new GraphQLConfiguration(app.configuration).getClient[getConsignmentDetailsForMetadataReview.Data, getConsignmentDetailsForMetadataReview.Variables]()
    val data: client.GraphqlData = client.GraphqlData(
      Some(
        getConsignmentDetailsForMetadataReview.Data(
          Some(
            getConsignmentDetailsForMetadataReview.GetConsignment(
              consignmentReference = "TDR-2024-TEST",
              seriesName = Some("SeriesName"),
              transferringBodyName = Some("TransferringBody"),
              userid = userId,
              totalClosedRecords = 2,
              includeTopLevelFolder = Some(false),
              totalFiles = 10,
              consignmentMetadata = List.empty,
              metadataReviewLogs = logs
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
}
