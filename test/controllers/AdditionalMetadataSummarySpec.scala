package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignment.getConsignment
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.Play.materializer
import play.api.http.Status.{FORBIDDEN, FOUND, OK}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.ConsignmentService
import uk.gov.nationalarchives.tdr.GraphQLClient.Error
import util.{CheckPageForStaticElements, FrontEndTestHelper}

import java.util.UUID
import scala.concurrent.ExecutionContext

class AdditionalMetadataSummarySpec extends FrontEndTestHelper {
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

  "AdditionalMetadataSummaryController" should {
    "render the additional metadata summary page" in {
      val consignmentId = UUID.randomUUID()
      val consignmentReference = "TEST-TDR-2021-GB"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, consignmentReference)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataSummaryController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller.getSelectedSummaryPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure/selected-summary"))
      val closureMetadataSummaryPage = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      closureMetadataSummaryPage.contains("FOI decision asserted") mustBe true
      closureMetadataSummaryPage.contains("Closure start date") mustBe true
      closureMetadataSummaryPage.contains("Closure period") mustBe true
      closureMetadataSummaryPage must include(
        """            <dt class="govuk-summary-list__key">
          |              Name
          |            </dt>
          |            <dd class="govuk-summary-list__value">
          |              Flour.txt
          |            </dd>""".stripMargin
      )
      closureMetadataSummaryPage must include(
        """            <dt class="govuk-summary-list__key">
          |              FOI example code
          |            </dt>
          |            <dd class="govuk-summary-list__value">
          |              Open
          |            </dd>""".stripMargin
      )
      closureMetadataSummaryPage must include(
        s"""        <div class="govuk-details__text">
           |            $consignmentReference
           |        </div>""".stripMargin
      )
      closureMetadataSummaryPage must include(
        s"""        <div class="govuk-details__text">
           |            $consignmentReference
           |        </div>""".stripMargin
      )
      closureMetadataSummaryPage.contains("Edit properties") mustBe true
      closureMetadataSummaryPage.contains("Abandon changes") mustBe true
      closureMetadataSummaryPage.contains("Save and return to all files") mustBe true

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "will return forbidden if the pages are accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataSummaryController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller.getSelectedSummaryPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure/selected-summary"))

      status(response) mustBe FORBIDDEN
    }

    "will return forbidden if the user does not own the consignment" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val client = new GraphQLConfiguration(app.configuration).getClient[getConsignment.Data, getConsignment.Variables]()
      val errors = Error(s"User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '$consignmentId'", Nil, Nil, None) :: Nil
      val dataString: String = client.GraphqlData(None, errors).asJson.noSpaces
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignment($consignmentId:UUID!,,$fileFiltersInput:FileFilters)"))
        .willReturn(okJson(dataString)))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataSummaryController(consignmentService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller.getSelectedSummaryPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure/selected-summary"))

      status(response) mustBe FORBIDDEN
    }

    "will redirect to the login page if the page is accessed by a logged out user" in {
      val consignmentId = UUID.randomUUID()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataSummaryController(consignmentService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller.getSelectedSummaryPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure/selected-summary"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "will return an error if no files exist for the consignment" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")

      val dataString = s"""{"data":{"getConsignment":{"consignmentReference":"TEST","files":[]}}}""".stripMargin
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentFilesMetadata($consignmentId:UUID!,$fileFiltersInput:FileFilters)"))
        .willReturn(okJson(dataString)))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataSummaryController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller.getSelectedSummaryPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure/selected-summary")).failed.futureValue

      response.getMessage mustBe s"Can't find selected files for the consignment $consignmentId"
    }

    "will return an error if the consignment doesn't exist" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")

      val dataString = """{"data":{"getConsignment":null},"errors":[]}"""
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentFilesMetadata($consignmentId:UUID!,$fileFiltersInput:FileFilters)"))
        .willReturn(okJson(dataString)))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val controller = new AdditionalMetadataSummaryController(consignmentService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller.getSelectedSummaryPage(consignmentId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/closure/selected-summary")).failed.futureValue

      response.getMessage mustBe s"No consignment found for consignment $consignmentId"
    }
  }
}
