package controllers

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, postRequestedFor, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignment.getConsignment
import io.circe.generic.auto.exportEncoder
import io.circe.syntax.EncoderOps
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.{ConsignmentService, CustomMetadataService}
import testUtils.{CheckPageForStaticElements, FrontEndTestHelper}
import uk.gov.nationalarchives.tdr.GraphQLClient.Error
import play.api.http.Status.{FORBIDDEN, FOUND, OK, SEE_OTHER}

import java.util.UUID
import scala.concurrent.ExecutionContext

class DeleteAdditionalMetadataControllerSpec extends FrontEndTestHelper {
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

  "confirmDeleteAdditionalMetadata" should {
    "render the delete closure metadata page with an authenticated user" in {
      val consignmentId = UUID.randomUUID()
      val consignmentReference = "TEST-TDR-2021-GB"
      val mockMetadataTypeAndValueString = mockMetadataTypeAndValue.head
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentFilesMetadataResponse(wiremockServer, consignmentReference, fileIds = List(UUID.randomUUID()))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new DeleteAdditionalMetadataController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .confirmDeleteAdditionalMetadata(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/${metadataType(0)}"))
      val deleteMetadataPage = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(deleteMetadataPage, userType = "standard", consignmentExists = false)
      deleteMetadataPage.contains("<title>Delete closure metadata</title>") mustBe true
      deleteMetadataPage.contains(
        """                    <h1 class="govuk-heading-xl">
          |                        Delete closure metadata
          |                    </h1>""".stripMargin
      ) mustBe true
      deleteMetadataPage.contains("If you proceed, closure metadata for file 'original/file/path' will be removed.") mustBe true
      deleteMetadataPage.contains("<p class=\"govuk-body\">Are you sure you would like to proceed?</p>") mustBe true

      val deleteButtonHref =
        s"/consignment/$consignmentId/additional-metadata/delete-metadata/${metadataType(0)}?fileIds=${fileIds.mkString("&amp;")}&amp;metadataTypeAndValueSelected=$mockMetadataTypeAndValueString"
      val cancelButtonHref =
        s"/consignment/$consignmentId/additional-metadata/selected-summary/${metadataType(0)}?fileIds=${fileIds.mkString("&amp;")}&amp;metadataTypeAndValueSelected=$mockMetadataTypeAndValueString"
      deleteMetadataPage.contains(
        s"""                    <div class="govuk-button-group">
           |                        <a href="$deleteButtonHref" role="button" draggable="false" class="govuk-button">
           |                            Delete and return to all files
           |                        </a>
           |                        <a class="govuk-link govuk-link--no-visited-state" href="$cancelButtonHref">
           |                            Cancel
           |                        </a>
           |                    </div>""".stripMargin
      ) mustBe true

      wiremockServer.verify(postRequestedFor(urlEqualTo("/graphql")))
    }

    "return forbidden if the page is accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new DeleteAdditionalMetadataController(consignmentService, customMetadataService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .confirmDeleteAdditionalMetadata(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/${metadataType(0)}"))

      status(response) mustBe FORBIDDEN
    }

    "return forbidden if the user does not own the consignment" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      val client = new GraphQLConfiguration(app.configuration).getClient[getConsignment.Data, getConsignment.Variables]()
      val errors = Error(s"User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '$consignmentId'", Nil, Nil, None) :: Nil
      val dataString: String = client.GraphqlData(None, errors).asJson.noSpaces
      wiremockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignmentFilesMetadata($consignmentId:UUID!,$fileFiltersInput:FileFilters)"))
          .willReturn(okJson(dataString))
      )

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new DeleteAdditionalMetadataController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .confirmDeleteAdditionalMetadata(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/${metadataType(0)}"))
        .failed
        .futureValue

      response.getMessage.contains(errors.head.message) mustBe true
    }

    "redirect to the login page if the page is accessed by a logged out user" in {
      val consignmentId = UUID.randomUUID()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new DeleteAdditionalMetadataController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller
        .confirmDeleteAdditionalMetadata(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/${metadataType(0)}"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "return an error if the fileIds are empty" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      val errorMessage = "fileIds are empty"

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new DeleteAdditionalMetadataController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .confirmDeleteAdditionalMetadata(consignmentId, metadataType(0), Nil, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/${metadataType(0)}"))
        .failed
        .futureValue

      response.getMessage.contains(errorMessage) mustBe true
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

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new DeleteAdditionalMetadataController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .confirmDeleteAdditionalMetadata(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/confirm-delete-metadata/${metadataType(0)}"))
        .failed
        .futureValue

      response.getMessage mustBe s"Can't find selected files for the consignment $consignmentId"
    }
  }

  "deleteAdditionalMetadata" should {
    "delete the metadata and redirect to the navigation page" in {
      val consignmentId = UUID.randomUUID()
      val parentFolderId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, None, parentFolderId = parentFolderId.some)
      setDeleteFileMetadataResponse(app.configuration, wiremockServer, fileIds, List("PropertyName1"))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller = new DeleteAdditionalMetadataController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .deleteAdditionalMetadata(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/delete-metadata/${metadataType(0)}"))

      status(response) mustBe SEE_OTHER

      redirectLocation(response) must be(Some(s"/consignment/$consignmentId/additional-metadata/files/mockMetadataType/"))
    }

    "return an error if the fileIds are empty" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      val errorMessage = "fileIds are empty"

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller = new DeleteAdditionalMetadataController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .deleteAdditionalMetadata(consignmentId, metadataType(0), Nil, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/delete-metadata/${metadataType(0)}"))
        .failed
        .futureValue

      response.getMessage.contains(errorMessage) mustBe true
    }

    "return forbidden if the url is accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller = new DeleteAdditionalMetadataController(consignmentService, customMetadataService, getValidJudgmentUserKeycloakConfiguration, getAuthorisedSecurityComponents)
      val response = controller
        .deleteAdditionalMetadata(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/delete-metadata/${metadataType(0)}"))

      status(response) mustBe FORBIDDEN
    }

    "redirect to the login page if the url is accessed by a logged out user" in {
      val consignmentId = UUID.randomUUID()
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val customMetadataService = new CustomMetadataService(graphQLConfiguration)
      val controller =
        new DeleteAdditionalMetadataController(consignmentService, customMetadataService, getValidStandardUserKeycloakConfiguration, getUnauthorisedSecurityComponents)
      val response = controller
        .deleteAdditionalMetadata(consignmentId, metadataType(0), fileIds, mockMetadataTypeAndValue)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/delete-metadata/${metadataType(0)}"))

      status(response) mustBe FOUND
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }
  }
}
