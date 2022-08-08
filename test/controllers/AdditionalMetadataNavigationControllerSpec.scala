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
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.Play.materializer
import play.api.cache.redis.{CacheApi, RedisSet, SynchronousResult}
import play.api.http.Status.{FORBIDDEN, OK, SEE_OTHER}
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.ConsignmentService
import testUtils.FrontEndTestHelper
import uk.gov.nationalarchives.tdr.GraphQLClient.Error

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

  implicit val ec: ExecutionContext = ExecutionContext.global
  val consignmentId: UUID = UUID.randomUUID()

  "AdditionalMetadataNavigationController" should {
    "render the additional metadata file selection page" in {
      val parentFolder = "parentFolder"
      val currentPage = 1
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder, folderId, fileId)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      contentType(response) mustBe Some("text/html")

      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
    }

    "render the additional metadata file selection page, pagination buttons up to the total pages" in {
      val parentFolder = "parentFolder"
      val currentPage = 1
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(3))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" value="1">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" value="2">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" value="3">""") mustBe true
    }

    "must not display the 'previous' button if you are on the first page" in {
      val parentFolder = "parentFolder"
      val currentPage = 1
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(3))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      fileSelectionPageAsString.contains(
        s"""
           |                <button name="pageSelected" data-prevent-double-click="true" class="govuk-button" type="submit" data-module="govuk-button" role="button" value="${currentPage + 1}">
           |                  Previous
           |                </button>""".stripMargin) mustBe false
    }

    "must display the 'previous' button if you are on a page other than the first page" in {
      val parentFolder = "parentFolder"
      val currentPage = 2
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(3))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      fileSelectionPageAsString.contains(
        s"""
           |                                <button class="govuk-button__tna-button-link" name="pageSelected" data-prevent-double-click="true" type="submit" data-module="govuk-button" role="link" value="${currentPage - 1}">
           |                                    Previous
           |                                </button>""".stripMargin) mustBe true
    }

    "must not display the 'next' button if you are on the last page" in {
      val parentFolder = "parentFolder"
      val currentPage = 3
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(3))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      fileSelectionPageAsString.contains(
        s"""
           |                <button name="pageSelected" data-prevent-double-click="true" class="govuk-button" type="submit" data-module="govuk-button" role="button" value="$currentPage">
           |                  Next
           |                </button>""".stripMargin) mustBe false
    }

    "must display the 'next' button if you are not on the last page" in {
      val parentFolder = "parentFolder"
      val currentPage = 1
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(3))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      fileSelectionPageAsString.contains(
        s"""
           |                                <button name="pageSelected" data-prevent-double-click="true" class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" value="2">
           |                                    Next
           |                                </button>""".stripMargin) mustBe true
    }

    "must redirect to the correct page when submitting a form" in {
      val selectedFolderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val page = "1"
      setConsignmentTypeResponse(wiremockServer, "standard")

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.submit(consignmentId, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId")
          .withFormUrlEncodedBody(
            Seq(
              ("allNodes[]", fileId.toString),
              ("selected[]", fileId.toString),
              ("pageSelected", page),
              ("folderSelected", selectedFolderId.toString)): _*)
          .withCSRFToken)

      status(response) mustBe SEE_OTHER

      redirectLocation(response) must be(Some(s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$page"))
    }

    "Must redirect to the correct page when submitting a form with 'returnToRoot' defined" in {
      val selectedFolderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val page = "1"
      setConsignmentTypeResponse(wiremockServer, "standard")

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.submit(consignmentId, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId")
          .withFormUrlEncodedBody(
            Seq(
              ("returnToRoot", selectedFolderId.toString),
              ("allNodes[]", fileId.toString),
              ("selected[]", fileId.toString),
              ("pageSelected", page),
              ("folderSelected", "folderSelected")): _*)
          .withCSRFToken)

      status(response) mustBe SEE_OTHER

      redirectLocation(response) must be(Some(s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$page"))
    }

    "Should correctly store selected file in the cache" in {
      val selectedFolderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val page = "1"
      setConsignmentTypeResponse(wiremockServer, "standard")

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.submit(consignmentId, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId")
          .withFormUrlEncodedBody(
            Seq(
              ("allNodes[]", fileId.toString),
              ("selected[]", fileId.toString),
              ("pageSelected", page),
              ("folderSelected", selectedFolderId.toString)): _*)
          .withCSRFToken)

      status(response) mustBe SEE_OTHER

      redirectLocation(response) must be(Some(s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$page"))
      Mockito.verify(redisSetMock).add(fileId)
    }

    "will return forbidden if the file selection page is accessed by a judgment user" in {
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

    "will return an error message if the user does not own the consignment" in {
      val currentPage = 1
      val selectedFolderId = UUID.randomUUID()
      val errorMessage = s"User '7bee3c41-c059-46f6-8e9b-9ba44b0489b7' does not own consignment '$consignmentId'"
      setConsignmentTypeResponse(wiremockServer, "standard")
      val client = new GraphQLConfiguration(app.configuration).getClient[getConsignment.Data, getConsignment.Variables]()
      val errors = Error(errorMessage, Nil, Nil, None) :: Nil
      val dataString: String = client.GraphqlData(None, errors).asJson.noSpaces
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentPaginatedFiles"))
        .willReturn(okJson(dataString)))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$currentPage").withCSRFToken).failed.futureValue

      response.getMessage.contains(errorMessage) mustBe true
    }

    "redirect to the auth server with an unauthenticated user" in {
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

  // scalastyle:off line.size.limit
  private def checkCommonFileNavigationElements(fileSelectionPageAsString: String,
                                                parentFolder: String,
                                                folderId: UUID,
                                                fileId: UUID,
                                                selectedFolderId: UUID): Unit = {
    fileSelectionPageAsString.contains(s"Add or edit closure metadata on file basis") mustBe true
    fileSelectionPageAsString.contains(s"Folder uploaded: $parentFolder") mustBe true
    fileSelectionPageAsString.contains(s"Add closure properties") mustBe true
    fileSelectionPageAsString.contains(
      s"""<button class="govuk-button__tna-button-link" name="folderSelected" data-prevent-double-click="true" type="submit" role="link" value="$folderId">""".stripMargin) mustBe true
    fileSelectionPageAsString.contains(
      s"""<label class="govuk-label govuk-checkboxes__label" for="$fileId">""") mustBe true
    fileSelectionPageAsString.contains(s"""<input type="hidden" id="folderSelected" name="folderSelected" value="$selectedFolderId"/>""") mustBe true
    fileSelectionPageAsString.contains(s"Back to closure metadata menu") mustBe true
  }
  // scalastyle:on line.size.limit

  private def setConsignmentPaginatedFilesResponse(wiremockServer: WireMockServer,
                                                   parentFolder: String,
                                                   folderId: UUID,
                                                   fileId: UUID,
                                                   totalPages: Option[Int] = Some(1)
                                                  ): StubMapping = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcpf.Data, gcpf.Variables]()
    val paginatedFiles: gcpf.GetConsignment.PaginatedFiles =
      PaginatedFiles(PageInfo(startCursor = None, endCursor = None, hasNextPage = true, hasPreviousPage = true),
        Some(List(
          Some(Edges(Edges.Node(fileId = folderId, fileName = Some(parentFolder), fileType = Some("Folder"), parentId = None))),
          Some(Edges(Edges.Node(fileId = fileId, fileName = Some("FileName"), fileType = Some("File"), parentId = Some(folderId)))))),
        totalPages = totalPages)
    val graphQlPaginatedData = gcpf.GetConsignment(parentFolder = Some(parentFolder), parentFolderId = Some(folderId),
      paginatedFiles = paginatedFiles)
    val response = gcpf.Data(Some(graphQlPaginatedData))
    val data = client.GraphqlData(Some(response))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getConsignmentPaginatedFiles"))
      .willReturn(okJson(dataString)))
  }
}
