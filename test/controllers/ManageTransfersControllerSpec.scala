package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.DateUtils
import graphql.codegen.GetConsignmentDetailsForMetadataReview.{getConsignmentDetailsForMetadataReview => gcdfmr}
import graphql.codegen.GetConsignmentReviewDetails.{getConsignmentReviewDetails => gcrd}
import io.circe.generic.auto._
import io.circe.syntax._
import org.dhatim.fastexcel.reader.{ReadableWorkbook, Row, Sheet}
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.matchers.should.Matchers._
import play.api.Play.materializer
import play.api.http.Status.{FORBIDDEN, FOUND, NOT_FOUND, OK}
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, header, redirectLocation, status, contentAsBytes}
import services.{ConsignmentService, MetadataReviewExportService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}

import java.io.ByteArrayInputStream
import java.time.temporal.ChronoUnit
import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

class ManageTransfersControllerSpec extends FrontEndTestHelper {
  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  val checkPageForStaticElements = new CheckPageForStaticElements

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val TNAUserType = "tna"
  private val userId = UUID.fromString("c140d49c-93d0-4345-8d71-c97ff28b947e")
  private val reviewerUserId = UUID.randomUUID()

  "ManageTransfersController GET" should {

    "render the manage transfers page for a TNA user" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")
      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(pageAsString, userType = TNAUserType, consignmentExists = false)
    }

    "render the page heading and tab navigation links" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      pageAsString must include("<h1 class=\"govuk-heading-l govuk-!-margin-bottom-5\">Manage transfers</h1>")
      pageAsString must include("""class="govuk-tabs__tab"""")
      pageAsString must include("Requested")
      pageAsString must include("Rejected")
      pageAsString must include("Approved")
      pageAsString must include("Transferred")
      pageAsString must include("All")
      pageAsString must include("""<a href="/admin/manage-transfers/download-review-history"""")
      pageAsString must include("Download all (Excel)")
    }

    "mark the requested tab as selected by default" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      pageAsString must include("govuk-tabs__list-item--selected")
      pageAsString must include("""aria-current="page">Requested""")
    }

    "mark the rejected tab as selected when tab=rejected" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(Some("rejected")).apply(FakeRequest(GET, "/admin/manage-transfers?tab=rejected").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      pageAsString must include("""aria-current="page">Rejected""")
      pageAsString must not include """aria-current="page">Requested"""
    }

    "render table column headers" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      pageAsString must include("""<th scope="col" class="govuk-table__header">Consignment</th>""")
      pageAsString must include("""<th scope="col" class="govuk-table__header">Status</th>""")
      pageAsString must include("""<th scope="col" class="govuk-table__header">Department</th>""")
      pageAsString must include("""<th scope="col" class="govuk-table__header">Series</th>""")
      pageAsString must include("""<th scope="col" class="govuk-table__header">Last updated</th>""")
    }

    "render consignment data with correct status tags" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      pageAsString must include("""<strong class="govuk-tag govuk-tag--red">Requested</strong>""")
      pageAsString must include regex s"""<a class="govuk-link" href="/admin/metadata-review/[0-9a-f-]+">TDR\u20112024\u2011TEST1</a>""".r
      // sanity: the reference must not contain ASCII hyphens in the rendered HTML
      pageAsString must not include """>TDR-2024-TEST1<"""
      pageAsString must include("TransferringBody1")
      pageAsString must include("SeriesName1")
    }

    "render correctly formatted last updated dates" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      val response = instantiateController().manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)
      val pageAsString = contentAsString(response)

      status(response) mustBe OK
      val dateTime = ZonedDateTime.now().minus(1, ChronoUnit.DAYS)
      pageAsString must include(s"${DateUtils.formatWithDaySuffixAndRelative(dateTime)}")
    }

    "return 403 if the page is accessed by a non-TNA user" in {
      val response = instantiateController(keycloakConfig = getValidKeycloakConfiguration).manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)

      status(response) mustBe FORBIDDEN
    }

    "redirect to the login page if accessed by a logged-out user" in {
      val response = instantiateController(keycloakConfig = getValidKeycloakConfiguration, securityComponents = getUnauthorisedSecurityComponents)
        .manageTransfers(None)
        .apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "return 404 if blockMetadataReviewV2 is true" in {
      val response = instantiateController(blockMetadataReviewV2 = true).manageTransfers(None).apply(FakeRequest(GET, "/admin/manage-transfers").withCSRFToken)

      status(response) mustBe NOT_FOUND
    }
  }

  "downloadMetadataReviewHistory" should {

    "return an Excel file with correct content type and headers" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      setGetConsignmentDetailsForMetadataReviewResponses(wiremockServer)

      val response = instantiateController().downloadMetadataReviewHistory().apply(FakeRequest(GET, "/admin/manage-transfers/download-review-history").withCSRFToken)

      status(response) mustBe OK
      contentType(response) mustBe Some("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
      header("Content-Disposition", response).get must startWith("attachment; filename=MetadataReviewHistory-")
      header("Content-Disposition", response).get must endWith(".xlsx")
    }

    "return an Excel file with correct column headers" in {
      setGetConsignmentReviewDetailsResponse(wiremockServer)
      setGetConsignmentDetailsForMetadataReviewResponses(wiremockServer)

      val response = instantiateController().downloadMetadataReviewHistory().apply(FakeRequest(GET, "/admin/manage-transfers/download-review-history").withCSRFToken)
      val bytes = contentAsBytes(response).toArray
      val wb = new ReadableWorkbook(new ByteArrayInputStream(bytes))
      val ws: Sheet = wb.getFirstSheet
      val rows: List[Row] = ws.read.asScala.toList
      val headerRow = rows.head

      headerRow.getCell(0).asString mustBe "Consignment Ref"
      headerRow.getCell(1).asString mustBe "Current Status"
      headerRow.getCell(2).asString mustBe "User Email Address"
      headerRow.getCell(3).asString mustBe "Last Updated"
      headerRow.getCell(4).asString mustBe "Department"
      headerRow.getCell(5).asString mustBe "Series"
      headerRow.getCell(6).asString mustBe "No. Records"
      headerRow.getCell(7).asString mustBe "No. Open"
      headerRow.getCell(8).asString mustBe "No. Closed"
      headerRow.getCell(9).asString mustBe "Submission No."
      headerRow.getCell(10).asString mustBe "Date Submitted"
      headerRow.getCell(11).asString mustBe "Review Action"
      headerRow.getCell(12).asString mustBe "Date Reviewed"
      headerRow.getCell(13).asString mustBe "Reviewed By"
      headerRow.getCell(14).asString mustBe "Reason for Action"

      wb.close()
    }

    "return rows with submission and review data" in {
      val consignmentId1 = UUID.randomUUID()
      setConsignmentReviewDetailsResponse(wiremockServer, consignmentId1, "TDR-2024-TEST1", "Rejected")
      setConsignmentDetailsForMetadataReviewResponse(
        wiremockServer,
        "TDR-2024-TEST1",
        List(
          gcdfmr.GetConsignment.MetadataReviewLogs(UUID.randomUUID(), consignmentId1, userId, "Submission", ZonedDateTime.parse("2024-07-05T10:34:00Z"), None),
          gcdfmr.GetConsignment.MetadataReviewLogs(UUID.randomUUID(), consignmentId1, reviewerUserId, "Rejection", ZonedDateTime.parse("2024-07-05T11:34:00Z"), Some("Bad data"))
        )
      )

      val response = instantiateController().downloadMetadataReviewHistory().apply(FakeRequest(GET, "/admin/manage-transfers/download-review-history").withCSRFToken)
      val bytes = contentAsBytes(response).toArray
      val wb = new ReadableWorkbook(new ByteArrayInputStream(bytes))
      val ws: Sheet = wb.getFirstSheet
      val rows: List[Row] = ws.read.asScala.toList

      rows.length mustBe 2

      val row = rows(1)
      row.getCell(0).asString mustBe "TDR-2024-TEST1"
      row.getCell(1).asString mustBe "Rejected"
      row.getCell(9).asString mustBe "1"
      row.getCell(11).asString mustBe "Rejected"
      row.getCell(14).asString mustBe "Bad data"

      wb.close()
    }

    "add a Transferred row when consignment status is Transferred" in {
      val consignmentId1 = UUID.randomUUID()
      setConsignmentReviewDetailsResponse(wiremockServer, consignmentId1, "TDR-2024-TRANS", "Transferred")
      setConsignmentDetailsForMetadataReviewResponse(
        wiremockServer,
        "TDR-2024-TRANS",
        List(
          gcdfmr.GetConsignment.MetadataReviewLogs(UUID.randomUUID(), consignmentId1, userId, "Submission", ZonedDateTime.parse("2024-07-12T10:00:00Z"), None),
          gcdfmr.GetConsignment.MetadataReviewLogs(UUID.randomUUID(), consignmentId1, reviewerUserId, "Approval", ZonedDateTime.parse("2024-07-12T10:15:00Z"), None)
        )
      )

      val response = instantiateController().downloadMetadataReviewHistory().apply(FakeRequest(GET, "/admin/manage-transfers/download-review-history").withCSRFToken)
      val bytes = contentAsBytes(response).toArray
      val wb = new ReadableWorkbook(new ByteArrayInputStream(bytes))
      val ws: Sheet = wb.getFirstSheet
      val rows: List[Row] = ws.read.asScala.toList

      // Should have 3 rows: header + Approved row + Transferred row
      rows.length mustBe 3

      val approvedRow = rows(1)
      approvedRow.getCell(11).asString mustBe "Approved"

      val transferredRow = rows(2)
      transferredRow.getCell(11).asString mustBe "Transferred"
      transferredRow.getCell(9).asString mustBe "1"

      wb.close()
    }

    "include a pending submission row when no review action follows" in {
      val consignmentId1 = UUID.randomUUID()
      setConsignmentReviewDetailsResponse(wiremockServer, consignmentId1, "TDR-2024-PEND", "Requested")
      setConsignmentDetailsForMetadataReviewResponse(
        wiremockServer,
        "TDR-2024-PEND",
        List(
          gcdfmr.GetConsignment.MetadataReviewLogs(UUID.randomUUID(), consignmentId1, userId, "Submission", ZonedDateTime.parse("2025-01-03T00:00:00Z"), None)
        )
      )

      val response = instantiateController().downloadMetadataReviewHistory().apply(FakeRequest(GET, "/admin/manage-transfers/download-review-history").withCSRFToken)
      val bytes = contentAsBytes(response).toArray
      val wb = new ReadableWorkbook(new ByteArrayInputStream(bytes))
      val ws: Sheet = wb.getFirstSheet
      val rows: List[Row] = ws.read.asScala.toList

      rows.length mustBe 2
      val row = rows(1)
      row.getCell(0).asString mustBe "TDR-2024-PEND"
      row.getCell(9).asString mustBe "1"
      row.getCell(11).asString mustBe ""

      wb.close()
    }

    "return 403 if the page is accessed by a non-TNA user" in {
      val response = instantiateController(keycloakConfig = getValidKeycloakConfiguration)
        .downloadMetadataReviewHistory()
        .apply(FakeRequest(GET, "/admin/manage-transfers/download-review-history").withCSRFToken)

      status(response) mustBe FORBIDDEN
    }
  }

  private def instantiateController(
      keycloakConfig: KeycloakConfiguration = getValidTNAUserKeycloakConfiguration(),
      securityComponents: SecurityComponents = getAuthorisedSecurityComponents,
      blockMetadataReviewV2: Boolean = false
  ) = {
    val consignmentService = new ConsignmentService(new GraphQLConfiguration(app.configuration))
    val config = getApplicationConfig(Map("featureAccessBlock.blockMetadataReviewV2" -> blockMetadataReviewV2))
    new ManageTransfersController(
      keycloakConfig,
      securityComponents,
      consignmentService,
      new MetadataReviewExportService(consignmentService, keycloakConfig, config),
      config
    )
  }

  private def setGetConsignmentDetailsForMetadataReviewResponses(wiremockServer: WireMockServer): Unit = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcdfmr.Data, gcdfmr.Variables]()
    val data: client.GraphqlData = client.GraphqlData(
      Some(
        gcdfmr.Data(
          Some(
            gcdfmr.GetConsignment(
              "TDR-2024-TEST",
              Some("SeriesName"),
              Some("TransferringBody"),
              userId,
              totalClosedRecords = 5,
              includeTopLevelFolder = Some(false),
              totalFiles = 10,
              consignmentMetadata = List.empty,
              metadataReviewLogs = List.empty
            )
          )
        )
      )
    )
    val dataString = data.asJson.noSpaces
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentDetailsForMetadataReview"))
        .willReturn(okJson(dataString))
    )
  }

  private def setConsignmentReviewDetailsResponse(
      wiremockServer: WireMockServer,
      consignmentId: UUID,
      consignmentReference: String,
      reviewStatus: String
  ): Unit = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcrd.Data, gcrd.Variables]()
    val consignments = List(
      gcrd.GetConsignmentReviewDetails(
        consignmentId = consignmentId,
        consignmentReference = consignmentReference,
        reviewStatus = reviewStatus,
        transferringBodyName = Some("Mock Dept"),
        seriesName = Some("Mock Series"),
        lastUpdated = ZonedDateTime.parse("2024-07-13T07:00:00Z")
      )
    )
    val data = client.GraphqlData(Some(gcrd.Data(consignments)))
    val dataString = data.asJson.noSpaces
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentReviewDetails"))
        .willReturn(okJson(dataString))
    )
  }

  private def setConsignmentDetailsForMetadataReviewResponse(
      wiremockServer: WireMockServer,
      consignmentReference: String,
      logs: List[gcdfmr.GetConsignment.MetadataReviewLogs]
  ): Unit = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcdfmr.Data, gcdfmr.Variables]()
    val data: client.GraphqlData = client.GraphqlData(
      Some(
        gcdfmr.Data(
          Some(
            gcdfmr.GetConsignment(
              consignmentReference,
              Some("Mock Series"),
              Some("Mock Dept"),
              userId,
              totalClosedRecords = 14,
              includeTopLevelFolder = Some(false),
              totalFiles = 87,
              consignmentMetadata = List.empty,
              metadataReviewLogs = logs
            )
          )
        )
      )
    )
    val dataString = data.asJson.noSpaces
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentDetailsForMetadataReview"))
        .willReturn(okJson(dataString))
    )
  }
}
