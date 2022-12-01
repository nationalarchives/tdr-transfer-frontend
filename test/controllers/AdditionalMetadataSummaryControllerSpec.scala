package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignment.getConsignment
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.Play.materializer
import play.api.http.Status.{FORBIDDEN, FOUND, OK}
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.{ConsignmentService, CustomMetadataService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
import uk.gov.nationalarchives.tdr.GraphQLClient.Error

import java.sql.Timestamp
import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext

class AdditionalMetadataSummaryControllerSpec extends FrontEndTestHelper {
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

  val fileIds: List[UUID] = List(UUID.randomUUID())

  private val mockMetadataTypeAndValue = List("mockMetadataType-mockMetadataValue")

  "AdditionalMetadataSummaryController" should {
    "render the additional metadata summary page for closure metadata type" in {
      val consignmentId = UUID.randomUUID()
      val consignmentReference = "TEST-TDR-2021-GB"
      val mockMetadataTypeAndValueString = mockMetadataTypeAndValue.head

      val closureStartDate = LocalDateTime.of(1990, 12, 1, 10, 0)
      val fileMetadata = List(
        GetConsignment.Files.FileMetadata("TitleClosed", "true"),
        GetConsignment.Files.FileMetadata("ClosurePeriod", "4"),
        GetConsignment.Files.FileMetadata("ClosureStartDate", Timestamp.valueOf(closureStartDate).toString)
      )
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, consignmentReference, fileIds = List(UUID.randomUUID()), fileMetadata = fileMetadata)
      setCustomMetadataResponse(wiremockServer)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${metadataType(0)}"))
      val closureMetadataSummaryPage = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(closureMetadataSummaryPage, userType = "standard")
      closureMetadataSummaryPage.contains(
        """        <h1 class="govuk-heading-xl">
          |          Review closure metadata changes
          |        </h1>""".stripMargin
      ) mustBe true
      closureMetadataSummaryPage.contains(
        """        <p class="govuk-body">You can edit, remove or save closure metadata here.</p>""".stripMargin
      ) mustBe true
      val href =
        s"/consignment/$consignmentId/additional-metadata/add/${metadataType(0)}/?propertyNameAndFieldSelected=$mockMetadataTypeAndValueString&amp;fileIds=${fileIds.mkString("&amp;")}"
      closureMetadataSummaryPage.contains(
        s"""          <a href="$href" role="button" draggable="false" class="govuk-button govuk-button" data-module="govuk-button">
          |            Edit metadata
          |          </a>""".stripMargin
      ) mustBe true
      val deleteMetadataButtonHref =
        s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/${metadataType(0)}?" +
          s"fileIds=${fileIds.mkString(" & amp; ")}&amp;metadataTypeAndValueSelected=$mockMetadataTypeAndValueString"
      closureMetadataSummaryPage.contains(
        s"""          <a href="$deleteMetadataButtonHref" role="button" draggable="false" class="govuk-button govuk-button--warning">
          |            Delete metadata
          |          </a>""".stripMargin
      ) mustBe true
      List(("Is the title closed?", "Yes"), ("Closure Period", "4 years"), ("Closure Start Date", "01/12/1990")).foreach { field =>
        closureMetadataSummaryPage.contains(
          s"""
             |            <div class="govuk-summary-list__row govuk-summary-list__row--no-border">
             |              <dt class="govuk-summary-list__key">
             |              ${field._1}
             |              </dt>
             |              <dd class="govuk-summary-list__value">
             |              ${field._2}
             |              </dd>
             |            </div>
             |""".stripMargin
        ) mustBe true
      }
      closureMetadataSummaryPage must include(
        """            <dt class="govuk-summary-list__key">
          |              Name
          |            </dt>
          |............
          |              <dd class="govuk-summary-list__value">
          |                FileName
          |              </dd>""".stripMargin.replaceAll("\\.", " ")
      )
      closureMetadataSummaryPage must include(
        s"""        <a href="/consignment/$consignmentId/additional-metadata/files/closure/" role="button" draggable="false" class="govuk-button" data-module="govuk-button">
        |          Save and return to all files
        |        </a>""".stripMargin
      )

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "return forbidden if the pages are accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${metadataType(0)}"))

      status(response) mustBe FORBIDDEN
    }

    "return forbidden if the user does not own the consignment" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val client = new GraphQLConfiguration(app.configuration).getClient[getConsignment.Data, getConsignment.Variables]()
      val errors = Error(s"User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '$consignmentId'", Nil, Nil, None) :: Nil
      val dataString: String = client.GraphqlData(None, errors).asJson.noSpaces
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignment($consignmentId:UUID!,,$fileFiltersInput:FileFilters)"))
          .willReturn(okJson(dataString))
      )

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${metadataType(0)}"))

      status(response) mustBe FORBIDDEN
    }

    "redirect to the login page if the page is accessed by a logged out user" in {
      val consignmentId = UUID.randomUUID()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${metadataType(0)}"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "return an error if no files exist for the consignment" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")

      val dataString = s"""{"data":{"getConsignment":{"consignmentReference":"TEST","files":[]}}}""".stripMargin
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentFilesMetadata($consignmentId:UUID!,$fileFiltersInput:FileFilters)"))
          .willReturn(okJson(dataString))
      )
      setCustomMetadataResponse(wiremockServer)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${metadataType(0)}"))
        .failed
        .futureValue

      response.getMessage mustBe s"Can't find selected files for the consignment $consignmentId"
    }

    "return an error if metadataType is not valid" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, fileIds, "inValid")
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/closure"))
        .failed
        .futureValue

      response.getMessage mustBe "Invalid metadata type: inValid"
    }

    "return an error if the consignment doesn't exist" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")

      val dataString = """{"data":{"getConsignment":null},"errors":[]}"""
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentFilesMetadata($consignmentId:UUID!,$fileFiltersInput:FileFilters)"))
          .willReturn(okJson(dataString))
      )

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new AdditionalMetadataSummaryController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .getSelectedSummaryPage(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/selected-summary/${metadataType(0)}"))
        .failed
        .futureValue

      response.getMessage mustBe s"No consignment found for consignment $consignmentId"
    }
  }
}
