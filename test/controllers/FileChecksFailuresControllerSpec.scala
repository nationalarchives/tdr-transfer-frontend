package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentFileCheckFailures.{getConsignmentFileCheckFailures => gcfcf}
import io.circe.generic.auto._
import io.circe.syntax._
import org.jsoup.Jsoup
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{status => playStatus, _}
import services.ConsignmentService
import services.Statuses.UploadType
import testUtils.FrontEndTestHelper

import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext
import scala.jdk.CollectionConverters._

class FileChecksFailuresControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val wiremockServer = new WireMockServer(9006)
  private val consignmentId = UUID.randomUUID()
  private val uploadDate = ZonedDateTime.parse("2026-07-05T07:00:00Z")

  override def beforeEach(): Unit = wiremockServer.start()

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "fileChecksFailures" should {
    "show the consignment details, formatted upload date, contact email and error report download" in {
      setGraphQLResponse(
        List(
          gcfcf.GetConsignmentFileCheckFailures.ConsignmentStatuses("Series", uploadDate.minusDays(1)),
          gcfcf.GetConsignmentFileCheckFailures.ConsignmentStatuses(UploadType.id, uploadDate)
        )
      )

      val response = controller.fileChecksFailures(consignmentId).apply(FakeRequest(GET, s"/admin/file-checks-failures/$consignmentId").withCSRFToken)
      val page = Jsoup.parse(contentAsString(response))

      playStatus(response) mustBe OK
      page.select(".govuk-summary-card__title").text() mustBe "Consignment TDR-2026-TEST"
      val summaryRows = page
        .select(".govuk-summary-list__row")
        .asScala
        .map { row =>
          row.select("dt").text() -> row.select("dd").text()
        }
        .toList
      summaryRows mustBe List(
        "Transferring body" -> "Body",
        "Series" -> "Series",
        "Date uploaded" -> "5th July 2026, 8:00am",
        "Contact email" -> "email@test.com"
      )
      page.select("a[href='mailto:email@test.com']").text() mustBe "email@test.com"
      page.select(s"a[href='/consignment/$consignmentId/file-checks-results/download-failures']").text() mustBe "Download error report"
    }

    "fail explicitly when the upload status is missing" in {
      setGraphQLResponse(List(gcfcf.GetConsignmentFileCheckFailures.ConsignmentStatuses("Series", uploadDate)))

      val response = controller.fileChecksFailures(consignmentId).apply(FakeRequest(GET, s"/admin/file-checks-failures/$consignmentId").withCSRFToken)

      val exception = intercept[IllegalStateException] {
        await(response)
      }
      exception.getMessage mustBe s"No upload status found for consignment $consignmentId"
    }
  }

  private def controller: FileChecksFailuresController = {
    val consignmentService = new ConsignmentService(new GraphQLConfiguration(app.configuration))
    new FileChecksFailuresController(getValidTNAUserKeycloakConfiguration(), getAuthorisedSecurityComponents, consignmentService)
  }

  private def setGraphQLResponse(statuses: List[gcfcf.GetConsignmentFileCheckFailures.ConsignmentStatuses]): Unit = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcfcf.Data, gcfcf.Variables]()
    val consignment = gcfcf.GetConsignmentFileCheckFailures(Some(consignmentId), "TDR-2026-TEST", Some("Body"), Some("Series"), UUID.randomUUID(), statuses)
    val data: client.GraphqlData = client.GraphqlData(Some(gcfcf.Data(List(consignment))))
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentFileCheckFailures"))
        .willReturn(okJson(data.asJson.noSpaces))
    )
  }
}
