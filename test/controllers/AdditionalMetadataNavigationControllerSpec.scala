package controllers

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignment.getConsignment
import graphql.codegen.GetConsignmentPaginatedFiles.getConsignmentPaginatedFiles.GetConsignment.PaginatedFiles
import graphql.codegen.GetConsignmentPaginatedFiles.getConsignmentPaginatedFiles.GetConsignment.PaginatedFiles.{Edges, PageInfo}
import graphql.codegen.GetConsignmentPaginatedFiles.{getConsignmentPaginatedFiles => gcpf}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.mockito.Mockito.when
import play.api.Play.materializer
import play.api.cache.redis.CacheApi
import play.api.http.Status.{FORBIDDEN, OK, SEE_OTHER}
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.ConsignmentService
import uk.gov.nationalarchives.tdr.GraphQLClient.Error
import util.{CheckPageForStaticElements, FrontEndTestHelper}

import java.util.UUID
import scala.concurrent.ExecutionContext

class AdditionalMetadataNavigationControllerSpec extends FrontEndTestHelper {
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

  "AdditionalMetadataNavigationController" should {
    "render the additional metadata file selection page" in {
      val parentFolder = "parentFolder"
      val consignmentId = UUID.randomUUID()
      val page = 1
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder))
      setConsignmentPaginatedFilesResponse(wiremockServer, folderId, fileId)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
//      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn()

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, page, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$page").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      fileSelectionPageAsString.contains(s"Folder uploaded: $parentFolder") mustBe true
      fileSelectionPageAsString.contains(s"Add closure properties") mustBe true
      fileSelectionPageAsString.contains(
        s"""<button class="folder-node" name="folderSelected" data-prevent-double-click="true" type="submit" role="button" value="$folderId">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""<label class="govuk-label govuk-checkboxes__label" for="$fileId">""") mustBe true
      fileSelectionPageAsString.contains(s"""<input type="hidden" id="pageSelected" name="pageSelected" value="$page"/>""") mustBe true
      fileSelectionPageAsString.contains(s"""<input type="hidden" id="folderSelected" name="folderSelected" value="$selectedFolderId"/>""") mustBe true
      fileSelectionPageAsString.contains(s"Back to closure metadata menu") mustBe true
    }

    "will return forbidden if the file selection pages is accessed by a judgment user" in {
      val consignmentId = UUID.randomUUID()
      val selectedFolderId = UUID.randomUUID()
      val page = 1
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidJudgmentUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, page, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$page").withCSRFToken)

      status(response) mustBe FORBIDDEN
    }

    "will return forbidden if the user does not own the consignment on the file selection page" in {
      val consignmentId = UUID.randomUUID()
      val selectedFolderId = UUID.randomUUID()
      val page = 1
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val client = new GraphQLConfiguration(app.configuration).getClient[getConsignment.Data, getConsignment.Variables]()
      val errors = Error(s"User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '$consignmentId'", Nil, Nil, None) :: Nil
      val dataString: String = client.GraphqlData(None, errors).asJson.noSpaces
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignment($consignmentId:UUID!)"))
        .willReturn(okJson(dataString)))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, page, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$page").withCSRFToken)

      status(response) mustBe FORBIDDEN
    }

    "render the additional metadata file selection page2" in {
      val parentFolder = "parentFolder"
      val consignmentId = UUID.randomUUID()
      val page = 1
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder))
      setConsignmentPaginatedFilesResponse(wiremockServer, folderId, fileId)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, page, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$page").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      //      checkPageForStaticElements.checkContentOfPagesThatUseMainScala(fileSelectionPageAsString, userType = "standard")

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      fileSelectionPageAsString.contains(s"Folder uploaded: $parentFolder") mustBe true
      fileSelectionPageAsString.contains(s"Add closure properties") mustBe true
      fileSelectionPageAsString.contains(
        s"""<button class="folder-node" name="folderSelected" data-prevent-double-click="true" type="submit" role="button" value="$folderId">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""<label class="govuk-label govuk-checkboxes__label" for="$fileId">""") mustBe true
      fileSelectionPageAsString.contains(s"""<input type="hidden" id="pageSelected" name="pageSelected" value="$page"/>""") mustBe true
      fileSelectionPageAsString.contains(s"""<input type="hidden" id="folderSelected" name="folderSelected" value="$selectedFolderId"/>""") mustBe true
      fileSelectionPageAsString.contains(s"Back to closure metadata menu") mustBe true
    }

    "redirect to the auth server with an unauthenticated user" in {
      val consignmentId = UUID.randomUUID()
      val selectedFolderId = UUID.randomUUID()
      val page = 1
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getUnauthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, page, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$page").withCSRFToken)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }
  }

  private def setConsignmentPaginatedFilesResponse(wiremockServer: WireMockServer,
                                                   folderId: UUID,
                                                   fileId: UUID,
                                                  ): StubMapping = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcpf.Data, gcpf.Variables]()
    val paginatedFiles: gcpf.GetConsignment.PaginatedFiles =
      PaginatedFiles(PageInfo(startCursor = None, endCursor = None, hasNextPage = true, hasPreviousPage = true),
        Some(List(
          Some(Edges(Edges.Node(fileId = folderId, fileName = Some("FolderName"), fileType = Some("Folder"), parentId = Some(UUID.randomUUID())))),
          Some(Edges(Edges.Node(fileId = fileId, fileName = Some("FileName"), fileType = Some("File"), parentId = Some(UUID.randomUUID())))))),
        totalPages = Some(1))
    val graphQlPaginatedData = gcpf.GetConsignment(parentFolder = Some("parentFolder"), parentFolderId = Some(UUID.randomUUID()),
      paginatedFiles = paginatedFiles)
    val response = gcpf.Data(Some(graphQlPaginatedData))
    val data = client.GraphqlData(Some(response))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getConsignmentPaginatedFiles"))
      .willReturn(okJson(dataString)))
  }
}
