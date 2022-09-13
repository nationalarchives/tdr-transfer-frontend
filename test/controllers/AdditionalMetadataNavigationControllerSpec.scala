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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.{never, reset, times, when}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import play.api.Play.materializer
import play.api.cache.redis.{CacheApi, RedisMap, RedisSet, SynchronousResult}
import play.api.http.Status.{FORBIDDEN, OK, SEE_OTHER}
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, contentAsString, contentType, defaultAwaitTimeout, redirectLocation, status}
import services.ConsignmentService
import testUtils.FrontEndTestHelper
import uk.gov.nationalarchives.tdr.GraphQLClient.Error

import java.util.UUID
import scala.concurrent.ExecutionContext

// scalastyle:off file.size.limit off
class AdditionalMetadataNavigationControllerSpec extends FrontEndTestHelper {
  val wiremockServer = new WireMockServer(9006)
  val selectedSet = mock[RedisSet[UUID, SynchronousResult]]
  val partSelectedSet = mock[RedisSet[UUID, SynchronousResult]]
  val folderMap = mock[RedisMap[List[UUID], SynchronousResult]]

  override def beforeEach(): Unit = {
    wiremockServer.start()
    reset(selectedSet)
    reset(partSelectedSet)
    reset(folderMap)
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  implicit val ec: ExecutionContext = ExecutionContext.global
  val consignmentId: UUID = UUID.randomUUID()

  "CacheSetHelper" should {

    val folderId = UUID.randomUUID()
    val file1Id = UUID.randomUUID()
    val file2Id = UUID.randomUUID()
    val file3Id = UUID.randomUUID()
    val file4Id = UUID.randomUUID()
    val allFolderFileDescendants = List(file1Id, file2Id, file3Id)

    val cacheApi = mock[CacheApi]

    when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(selectedSet)
    when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(partSelectedSet)
    when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(folderMap)

    "add folder to 'folder descendant cache' where folder descendants are not already cached" in {
      val metadataType = "closure"
      val selectedNodes = List(file1Id)
      val allDisplayedNodes = allFolderFileDescendants

      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, List(file1Id), List(file2Id, file3Id), allFolderFileDescendants)
      folderCacheResponseMocking(folderMap, folderId, allFolderFileDescendants, false)

      when(selectedSet.contains(file1Id)).thenReturn(true)

      val controller = setUpController(cacheApi)
      val formPostData = setUpFormPostData(selectedNodes, allDisplayedNodes, folderId)

      controller.submit(consignmentId, limit = None, selectedFolderId = folderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$folderId")
          .withFormUrlEncodedBody(
            formPostData: _*)
          .withCSRFToken).futureValue

      Mockito.verify(folderMap).add(folderId.toString, allFolderFileDescendants)
      Mockito.verify(partSelectedSet).remove(folderId)
      Mockito.verify(partSelectedSet).add(folderId)
      Mockito.verify(selectedSet).add(file1Id)
    }

    "add a single file to the 'selected cache' when single file selected" in {
      val metadataType = "closure"
      val selectedNodes = List(file1Id)
      val allDisplayedNodes = allFolderFileDescendants

      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, List(file1Id), List(file2Id, file3Id))
      folderCacheResponseMocking(folderMap, folderId, allFolderFileDescendants)

      when(selectedSet.contains(file1Id)).thenReturn(true)

      val controller = setUpController(cacheApi)
      val formPostData = setUpFormPostData(selectedNodes, allDisplayedNodes, folderId)

      controller.submit(consignmentId, limit = None, selectedFolderId = folderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$folderId")
          .withFormUrlEncodedBody(
            formPostData: _*)
          .withCSRFToken).futureValue

      Mockito.verify(folderMap, never()).add(any[String], any[List[UUID]])
      Mockito.verify(partSelectedSet).remove(folderId)
      Mockito.verify(partSelectedSet).add(folderId)
      Mockito.verify(selectedSet).add(file1Id)
    }

    "remove single previously selected file from the 'selected cache' when file deselected" in {
      val metadataType = "closure"
      val selectedNodes = List()
      val allDisplayedNodes = allFolderFileDescendants
      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, List(), allFolderFileDescendants)
      folderCacheResponseMocking(folderMap, folderId, allFolderFileDescendants)

      //initially id will be present, but then removed for subsequent "contains" calls
      when(selectedSet.contains(file1Id))
        .thenReturn(true)
        .thenReturn(false)

      val controller = setUpController(cacheApi)
      val formPostData = setUpFormPostData(selectedNodes, allDisplayedNodes, folderId)
      val response = controller.submit(consignmentId, limit = None, selectedFolderId = folderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$folderId")
          .withFormUrlEncodedBody(
            formPostData: _*)
          .withCSRFToken)

      status(response) mustBe SEE_OTHER

      redirectLocation(response) must be(Some(s"/consignment/$consignmentId/additional-metadata/$metadataType/$folderId/1"))

      Mockito.verify(folderMap, never()).add(any[String], any[List[UUID]])
      Mockito.verify(partSelectedSet).remove(folderId)
      Mockito.verify(partSelectedSet, never()).add(any[UUID])
      Mockito.verify(selectedSet).remove(file1Id)
    }

    "add and remove multiple files from 'selected cache' depending on selection and de-selection options" in {
      val metadataType = "closure"
      val selectedNodes = List(file3Id, file4Id)
      val allDisplayedNodes = allFolderFileDescendants :+ file4Id
      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, List(file3Id, file4Id), allFolderFileDescendants :+ file4Id)
      folderCacheResponseMocking(folderMap, folderId, allFolderFileDescendants :+ file4Id)

      //initially id will not be present, but then added for subsequent "contains" calls
      selectedNodes.map(id => {
        when(selectedSet.contains(id))
          .thenReturn(false)
          .thenReturn(true)
      })

      //initially id will be present, but then removed for subsequent "contains" calls
      List(file1Id, file2Id).map(id => {
        when(selectedSet.contains(id))
          .thenReturn(true)
          .thenReturn(false)
      })

      val controller = setUpController(cacheApi)
      val formPostData = setUpFormPostData(selectedNodes, allDisplayedNodes, folderId)
      val response = controller.submit(consignmentId, limit = None, selectedFolderId = folderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$folderId")
          .withFormUrlEncodedBody(
            formPostData: _*)
          .withCSRFToken)

      status(response) mustBe SEE_OTHER

      redirectLocation(response) must be(Some(s"/consignment/$consignmentId/additional-metadata/$metadataType/$folderId/1"))

      Mockito.verify(folderMap, never()).add(any[String], any[List[UUID]])
      Mockito.verify(partSelectedSet).remove(folderId)
      Mockito.verify(partSelectedSet).add(folderId)
      Mockito.verify(selectedSet, times(1)).add(file3Id)
      Mockito.verify(selectedSet, times(1)).add(file4Id)
      Mockito.verify(selectedSet, times(1)).remove(file1Id)
      Mockito.verify(selectedSet, times(1)).remove(file2Id)
    }

    "add all folder descendants to 'selected cache' if folder selected" in {
      val metadataType = "closure"
      val selectedNodes = List(folderId)
      val allDisplayedNodes = List(folderId)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, allFolderFileDescendants, List())
      folderCacheResponseMocking(folderMap, folderId, allFolderFileDescendants)

      allFolderFileDescendants.map(id => when(selectedSet.contains(id)).thenReturn(true))

      val controller = setUpController(cacheApi)
      val formPostData = setUpFormPostData(selectedNodes, allDisplayedNodes, folderId)

      controller.submit(consignmentId, limit = None, selectedFolderId = folderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$folderId")
          .withFormUrlEncodedBody(
            formPostData: _*)
          .withCSRFToken).futureValue

      Mockito.verify(folderMap, never()).add(any[String], any[List[UUID]])
      Mockito.verify(partSelectedSet).remove(folderId)
      Mockito.verify(partSelectedSet, never()).add(folderId)
      Mockito.verify(selectedSet, times(1)).add(file1Id)
      Mockito.verify(selectedSet, times(1)).add(file2Id)
      Mockito.verify(selectedSet, times(1)).add(file3Id)
    }

    "remove folder from 'part selected cache' if all folder descendants are selected" in {
      val metadataType = "closure"
      val selectedNodes = List(file1Id)
      val allDisplayedNodes = allFolderFileDescendants
      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, List(file1Id, file2Id, file3Id), List())
      folderCacheResponseMocking(folderMap, folderId, allFolderFileDescendants)

      allFolderFileDescendants.map(id => when(selectedSet.contains(id)).thenReturn(true))

      val controller = setUpController(cacheApi)
      val formPostData = setUpFormPostData(selectedNodes, allDisplayedNodes, folderId)

      controller.submit(consignmentId, limit = None, selectedFolderId = folderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$folderId")
          .withFormUrlEncodedBody(
            formPostData: _*)
          .withCSRFToken).futureValue

      Mockito.verify(folderMap, never()).add(any[String], any[List[UUID]])
      Mockito.verify(partSelectedSet).remove(folderId)
      Mockito.verify(partSelectedSet, never()).add(any[UUID])
      Mockito.verify(selectedSet, times(1)).add(file1Id)
      Mockito.verify(selectedSet, times(1)).add(file2Id)
      Mockito.verify(selectedSet, times(1)).add(file3Id)
    }

    "add folder to 'part selected cache' if some, but not all descendants are selected" in {
      val metadataType = "closure"
      val selectedNodes = List(file1Id)
      val allDisplayedNodes = List(file1Id, file2Id, file3Id)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, allFolderFileDescendants, List())
      folderCacheResponseMocking(folderMap, folderId, allFolderFileDescendants)

      when(selectedSet.contains(file1Id)).thenReturn(true)
      when(selectedSet.contains(file2Id)).thenReturn(false)
      when(selectedSet.contains(file3Id)).thenReturn(false)

      val controller = setUpController(cacheApi)
      val formPostData = setUpFormPostData(selectedNodes, allDisplayedNodes, folderId)

      controller.submit(consignmentId, limit = None, selectedFolderId = folderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$folderId")
          .withFormUrlEncodedBody(
            formPostData: _*)
          .withCSRFToken).futureValue

      Mockito.verify(folderMap, never()).add(any[String], any[List[UUID]])
      Mockito.verify(partSelectedSet).remove(folderId)
      Mockito.verify(partSelectedSet).add(folderId)
      Mockito.verify(selectedSet).add(file1Id)
    }

    "add folder to 'part selected cache' if some, but not all descendants, are de-selected" in {
      val metadataType = "closure"
      val selectedNodes = List(file1Id, file2Id)
      val allDisplayedNodes = List(file1Id, file2Id, file3Id)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, List(file1Id, file2Id), List(file3Id))
      folderCacheResponseMocking(folderMap, folderId, allFolderFileDescendants)

      when(selectedSet.contains(file3Id))
        .thenReturn(true)
        .thenReturn(false)
      when(selectedSet.contains(file1Id)).thenReturn(true)
      when(selectedSet.contains(file2Id)).thenReturn(true)

      val controller = setUpController(cacheApi)
      val formPostData = setUpFormPostData(selectedNodes, allDisplayedNodes, folderId)

      controller.submit(consignmentId, limit = None, selectedFolderId = folderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$folderId")
          .withFormUrlEncodedBody(
            formPostData: _*)
          .withCSRFToken).futureValue

      Mockito.verify(folderMap, never()).add(any[String], any[List[UUID]])
      Mockito.verify(partSelectedSet).remove(folderId)
      Mockito.verify(partSelectedSet).add(folderId)
      Mockito.verify(selectedSet).remove(file3Id)
      Mockito.verify(selectedSet, times(1)).add(file1Id)
      Mockito.verify(selectedSet, times(1)).add(file2Id)
    }

    "remove all folder descendants from 'selected cache' and remove folder from 'part selected cache' when folder is de-selected" in {
      val metadataType = "closure"
      val selectedNodes = List()
      val allDisplayedNodes = List(folderId)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, List(), List())
      folderCacheResponseMocking(folderMap, folderId, allFolderFileDescendants)

      allFolderFileDescendants.map(id => when(selectedSet.contains(id))
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(false))

      val controller = setUpController(cacheApi)
      val formPostData = setUpFormPostData(selectedNodes, allDisplayedNodes, folderId)

      controller.submit(consignmentId, limit = None, selectedFolderId = folderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$folderId")
          .withFormUrlEncodedBody(
            formPostData: _*)
          .withCSRFToken).futureValue

      Mockito.verify(folderMap, never()).add(any[String], any[List[UUID]])
      Mockito.verify(partSelectedSet).remove(folderId)
      Mockito.verify(partSelectedSet, never()).add(folderId)
      Mockito.verify(selectedSet, times(1)).remove(file1Id)
      Mockito.verify(selectedSet, times(1)).remove(file2Id)
      Mockito.verify(selectedSet, times(1)).remove(file3Id)
    }

    "not add an empty folder to 'selected cache' or 'part selected cache' if selected" in {
      val metadataType = "closure"
      val selectedNodes = List()
      val allDisplayedNodes = List(folderId)
      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, List(), List())
      folderCacheResponseMocking(folderMap, folderId, List())

      val controller = setUpController(cacheApi)
      val formPostData = setUpFormPostData(selectedNodes, allDisplayedNodes, folderId)

      controller.submit(consignmentId, limit = None, selectedFolderId = folderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$folderId")
          .withFormUrlEncodedBody(
            formPostData: _*)
          .withCSRFToken).futureValue

      Mockito.verify(folderMap, never()).add(any[String], any[List[UUID]])
      Mockito.verify(partSelectedSet).remove(folderId)
      Mockito.verify(partSelectedSet, never()).add(folderId)
      Mockito.verify(selectedSet, never()).add(any[UUID])
    }
  }

  "AdditionalMetadataNavigationController" should {
    "render the additional metadata file selection page" in {
      val parentFolder = "parentFolder"
      val currentPage = 1
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder, folderId, fileId)
      setAllDescendantIdsResponse(wiremockServer, List(), List())

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisSetMock.toSet).thenReturn(Set())
      when(redisMapMock.get(folderId.toString)).thenReturn(Some(List()))
      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$currentPage").withCSRFToken)
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
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(3))
      setAllDescendantIdsResponse(wiremockServer, List(), List())

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisMapMock.get(folderId.toString)).thenReturn(Some(List()))
      when(redisSetMock.toSet).thenReturn(Set())

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 1" value="1">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 2" value="2">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 3" value="3">""") mustBe true
    }

    "not display the 'previous' button if you are on the first page" in {
      val parentFolder = "parentFolder"
      val currentPage = 1
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(3))
      setAllDescendantIdsResponse(wiremockServer, List(), List())

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisMapMock.get(folderId.toString)).thenReturn(Some(List()))
      when(redisSetMock.toSet).thenReturn(Set())

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      // scalastyle:off line.size.limit
      fileSelectionPageAsString.contains(
        s"""
           |                <button name="pageSelected" data-prevent-double-click="true" class="govuk-button" type="submit data-module="govuk-button" role="button" value="${currentPage + 1}">
           |                  Previous
           |                </button>""".stripMargin
      ) mustBe false
      // scalastyle:on line.size.limit
    }

    "display the 'previous' button if you are on a page other than the first page" in {
      val parentFolder = "parentFolder"
      val currentPage = 2
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(3))
      setAllDescendantIdsResponse(wiremockServer, List(), List())

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisMapMock.get(folderId.toString)).thenReturn(Some(List()))
      when(redisSetMock.toSet).thenReturn(Set())

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      // scalastyle:off line.size.limit
      fileSelectionPageAsString.contains(
        s"""
           |                                <button name="pageSelected" data-prevent-double-click="true" class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" value="${currentPage - 1}">
           |                                    Previous
           |                                </button>""".stripMargin
      ) mustBe true
      // scalastyle:on line.size.limit
    }

    "not display the 'next' button if you are on the last page" in {
      val parentFolder = "parentFolder"
      val currentPage = 3
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(3))
      setAllDescendantIdsResponse(wiremockServer, List(), List())

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisMapMock.get(folderId.toString)).thenReturn(Some(List()))
      when(redisSetMock.toSet).thenReturn(Set())

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      // scalastyle:off line.size.limit
      fileSelectionPageAsString.contains(
        """
           |                <button name="pageSelected" data-prevent-double-click="true" class="govuk-button" type="submit" data-module="govuk-button" role="button" value="$currentPage">
           |                  Next
           |                </button>""".stripMargin
      ) mustBe false
      // scalastyle:on line.size.limit
    }

    "display the 'next' button if you are not on the last page" in {
      val parentFolder = "parentFolder"
      val currentPage = 1
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(3))
      setAllDescendantIdsResponse(wiremockServer, List(), List())

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisMapMock.get(folderId.toString)).thenReturn(Some(List()))
      when(redisSetMock.toSet).thenReturn(Set())

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      // scalastyle:off line.size.limit
      fileSelectionPageAsString.contains(
        s"""
           |                                <button name="pageSelected" data-prevent-double-click="true" class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" value="2">
           |                                    Next
           |                                </button>""".stripMargin
      ) mustBe true
      // scalastyle:on line.size.limit
    }

    "redirect to the correct page when submitting a form" in {
      val selectedFolderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val page = "1"
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, List(fileId))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisMapMock.get(selectedFolderId.toString)).thenReturn(Some(List()))

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.submit(consignmentId, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId")
          .withFormUrlEncodedBody(
            Seq(
              ("allNodes[]", fileId.toString),
              ("selected[]", fileId.toString),
              ("pageSelected", page),
              ("folderSelected", selectedFolderId.toString)): _*)
          .withCSRFToken)

      status(response) mustBe SEE_OTHER

      redirectLocation(response) must be(Some(s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$page"))
    }

    "redirect to the correct page when submitting a form with 'returnToRoot' defined" in {
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val page = "1"
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, List(fileId))

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisMapMock.get(folderId.toString)).thenReturn(Some(List()))

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.submit(consignmentId, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId")
          .withFormUrlEncodedBody(
            Seq(
              ("returnToRoot", selectedFolderId.toString),
              ("allNodes[]", fileId.toString),
              ("selected[]", fileId.toString),
              ("pageSelected", page),
              ("folderSelected", folderId.toString)): _*)
          .withCSRFToken)

      status(response) mustBe SEE_OTHER

      redirectLocation(response) must be(Some(s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$page"))
    }

    "should correctly store selected file in the cache" in {
      val selectedFolderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val page = "1"
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setAllDescendantIdsResponse(wiremockServer, List(fileId), List())

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisSetPartSelectedMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisMapMock.getFields(selectedFolderId.toString)).thenReturn(List())
      when(redisMapMock.get(selectedFolderId.toString)).thenReturn(Some(List()))
      when(redisMapMock.contains(selectedFolderId.toString)).thenReturn(true)

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetPartSelectedMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.submit(consignmentId, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId")
          .withFormUrlEncodedBody(
            Seq(
              ("allNodes[]", fileId.toString),
              ("selected[]", fileId.toString),
              ("pageSelected", page),
              ("folderSelected", selectedFolderId.toString)): _*)
          .withCSRFToken)

      status(response) mustBe SEE_OTHER

      redirectLocation(response) must be(Some(s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$page"))

      Mockito.verify(redisSetPartSelectedMock).remove(selectedFolderId)
      Mockito.verify(redisSetMock, times(1)).add(fileId)
    }

    "return forbidden if the file selection page is accessed by a judgment user" in {
      val selectedFolderId = UUID.randomUUID()
      val page = 1
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidJudgmentUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, page, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$selectedFolderId/$page").withCSRFToken)

      status(response) mustBe FORBIDDEN
    }

    "return an error message if the user does not own the consignment" in {
      val currentPage = 1
      val selectedFolderId = UUID.randomUUID()
      val metadataType = "closure"
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
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$currentPage")
          .withCSRFToken).failed.futureValue

      response.getMessage.contains(errorMessage) mustBe true
    }

    "redirect to the auth server with an unauthenticated user" in {
      val selectedFolderId = UUID.randomUUID()
      val page = 1
      val metadataType = "closure"
      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getUnauthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, page, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(POST, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$page").withCSRFToken)

      status(response) mustBe SEE_OTHER
      redirectLocation(response).get must startWith("/auth/realms/tdr/protocol/openid-connect/auth")
    }

    "display the correct file totals with a limit set" in {
      val parentFolder = "parentFolder"
      val currentPage = 2
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(3), totalFiles = Some(10))
      setAllDescendantIdsResponse(wiremockServer, List(fileId), List())

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisMapMock.get(folderId.toString)).thenReturn(Some(List()))
      when(redisSetMock.toSet).thenReturn(Set())

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = Option(3), selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)

      // scalastyle:off line.size.limit
      fileSelectionPageAsString.contains(
        """Showing <span class="govuk-body govuk-!-font-weight-bold">4</span> to <span class="govuk-body govuk-!-font-weight-bold">6</span> of <span class="govuk-body govuk-!-font-weight-bold">10</span> results"""
      ) mustBe true
      // scalastyle:on line.size.limit
    }

    "display the correct file totals when there a no files" in {
      val parentFolder = "parentFolder"
      val currentPage = 1
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setEmptyConsignmentPaginatedFilesResponse(wiremockServer, parentFolder, folderId)

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = Option(3), selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      // scalastyle:off line.size.limit
      fileSelectionPageAsString.contains(
        """Showing <span class="govuk-body govuk-!-font-weight-bold">0</span> to <span class="govuk-body govuk-!-font-weight-bold">0</span> of <span class="govuk-body govuk-!-font-weight-bold">0</span> results"""
      ) mustBe true
      fileSelectionPageAsString.contains(
        """
          |                <button name="pageSelected" data-prevent-double-click="true" class="govuk-button" type="submit" data-module="govuk-button" role="button" value="$currentPage">
          |                  Next
          |                </button>""".stripMargin
      ) mustBe false
      // scalastyle:on line.size.limit
    }

    "render the additional metadata file selection page, pagination buttons up to the total pages for large number of pages" in {
      val parentFolder = "parentFolder"
      val currentPage = 5
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(9))
      setAllDescendantIdsResponse(wiremockServer, List(fileId), List())

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisMapMock.get(folderId.toString)).thenReturn(Some(List()))
      when(redisSetMock.toSet).thenReturn(Set())

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 1" value="1">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 2" value="2">""") mustBe false
      fileSelectionPageAsString.contains(
        s"""<li class="govuk-pagination__item govuk-pagination__item--ellipses">&ctdot;</li>""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 3" value="3">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 4" value="4">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 5" value="5">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 6" value="6">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 7" value="7">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""<li class="govuk-pagination__item govuk-pagination__item--ellipses">&ctdot;</li>""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 8" value="8">""") mustBe false
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 9" value="9">""") mustBe true
    }

    "render the additional metadata file selection page, pagination buttons up to the total pages for large number of pages when on the first page" in {
      val parentFolder = "parentFolder"
      val currentPage = 1
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(5))
      setAllDescendantIdsResponse(wiremockServer, List(fileId), List())

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisMapMock.get(folderId.toString)).thenReturn(Some(List()))
      when(redisSetMock.toSet).thenReturn(Set())

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 1" value="1">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 2" value="2">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 3" value="3">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""<li class="govuk-pagination__item govuk-pagination__item--ellipses">&ctdot;</li>""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 4" value="4">""") mustBe false
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 5" value="5">""") mustBe true
    }

    "render the additional metadata file selection page, pagination buttons up to the total pages for large number of pages when on the last page" in {
      val parentFolder = "parentFolder"
      val currentPage = 5
      val selectedFolderId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val fileId = UUID.randomUUID()
      val metadataType = "closure"
      setConsignmentTypeResponse(wiremockServer, "standard")
      setConsignmentDetailsResponse(wiremockServer, Option(parentFolder), parentFolderId = None)
      setConsignmentPaginatedFilesResponse(wiremockServer, parentFolder: String, folderId, fileId, totalPages = Some(5))
      setAllDescendantIdsResponse(wiremockServer, List(fileId), List())

      val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
      val consignmentService = new ConsignmentService(graphQLConfiguration)
      val cacheApi = mock[CacheApi]
      val redisSetMock = mock[RedisSet[UUID, SynchronousResult]]
      val redisMapMock = mock[RedisMap[List[UUID], SynchronousResult]]

      when(redisMapMock.get(folderId.toString)).thenReturn(Some(List()))
      when(redisSetMock.toSet).thenReturn(Set())

      when(cacheApi.set[UUID](consignmentId.toString)).thenReturn(redisSetMock)
      when(cacheApi.set[UUID](s"${consignmentId.toString}_partSelected")).thenReturn(redisSetMock)
      when(cacheApi.map[List[UUID]](s"${consignmentId.toString}_folders")).thenReturn(redisMapMock)

      val controller = new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
        getAuthorisedSecurityComponents, cacheApi)
      val response = controller.getPaginatedFiles(consignmentId, currentPage, limit = None, selectedFolderId = selectedFolderId, metadataType)
        .apply(FakeRequest(GET, s"/consignment/$consignmentId/additional-metadata/$metadataType/$selectedFolderId/$currentPage").withCSRFToken)
      val fileSelectionPageAsString = contentAsString(response)

      status(response) mustBe OK
      checkCommonFileNavigationElements(fileSelectionPageAsString, parentFolder, folderId, fileId, selectedFolderId)
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 1" value="1">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 2" value="2">""") mustBe false
      fileSelectionPageAsString.contains(
        s"""<li class="govuk-pagination__item govuk-pagination__item--ellipses">&ctdot;</li>""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 3" value="3">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 4" value="4">""") mustBe true
      fileSelectionPageAsString.contains(
        s"""class="govuk-button__tna-button-link" type="submit" data-module="govuk-button" role="link" aria-label="Page 5" value="5">""") mustBe true
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
      s"""<button class="govuk-button__tna-button-link" name="folderSelected" data-prevent-double-click="true" type="submit" role="link" value="$folderId">""") mustBe true
    fileSelectionPageAsString.contains(
      s"""<label class="govuk-label govuk-checkboxes__label" for="$fileId">""") mustBe true
    fileSelectionPageAsString.contains(s"""<input type="hidden" id="folderSelected" name="folderSelected" value="$selectedFolderId"/>""") mustBe true
    fileSelectionPageAsString.contains(s"Back to overview") mustBe true
  }
  // scalastyle:on line.size.limit

  private def setConsignmentPaginatedFilesResponse(wiremockServer: WireMockServer,
                                                   parentFolder: String,
                                                   folderId: UUID,
                                                   fileId: UUID,
                                                   totalPages: Option[Int] = Some(1),
                                                   totalFiles: Option[Int] = Some(1)
                                                  ): StubMapping = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcpf.Data, gcpf.Variables]()
    val paginatedFiles: gcpf.GetConsignment.PaginatedFiles =
      PaginatedFiles(PageInfo(startCursor = None, endCursor = None, hasNextPage = true, hasPreviousPage = true),
        Some(List(
          Some(Edges(Edges.Node(fileId = folderId, fileName = Some(parentFolder), fileType = Some("Folder"), parentId = None))),
          Some(Edges(Edges.Node(fileId = fileId, fileName = Some("FileName"), fileType = Some("File"), parentId = Some(folderId)))))),
        totalPages = totalPages, totalItems = totalFiles)
    val graphQlPaginatedData = gcpf.GetConsignment(parentFolder = Some(parentFolder), parentFolderId = Some(folderId),
      paginatedFiles = paginatedFiles)
    val response = gcpf.Data(Some(graphQlPaginatedData))
    val data = client.GraphqlData(Some(response))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getConsignmentPaginatedFiles"))
      .willReturn(okJson(dataString)))
  }

  private def setEmptyConsignmentPaginatedFilesResponse(wiremockServer: WireMockServer,
                                                   parentFolder: String,
                                                   folderId: UUID
                                                  ): StubMapping = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcpf.Data, gcpf.Variables]()
    val paginatedFiles: gcpf.GetConsignment.PaginatedFiles =
      PaginatedFiles(PageInfo(startCursor = None, endCursor = None, hasNextPage = true, hasPreviousPage = true),
        Some(List.empty),
        totalPages = Some(0), totalItems = Some(0))
    val graphQlPaginatedData = gcpf.GetConsignment(parentFolder = Some(parentFolder), parentFolderId = Some(folderId),
      paginatedFiles = paginatedFiles)
    val response = gcpf.Data(Some(graphQlPaginatedData))
    val data = client.GraphqlData(Some(response))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getConsignmentPaginatedFiles"))
      .willReturn(okJson(dataString)))
  }

  private def setUpController(cacheApi: CacheApi): AdditionalMetadataNavigationController = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)

    new AdditionalMetadataNavigationController(consignmentService, getValidStandardUserKeycloakConfiguration,
      getAuthorisedSecurityComponents, cacheApi)
  }

  private def folderCacheResponseMocking(
                                          folderMap: RedisMap[List[UUID], SynchronousResult],
                                          folderId: UUID,
                                          descendants: List[UUID],
                                          contains: Boolean = true) = {
    when(folderMap.getFields(folderId.toString)).thenReturn(Seq(Some(descendants)))
    when(folderMap.get(folderId.toString)).thenReturn(Some(descendants))
    when(folderMap.contains(folderId.toString)).thenReturn(contains)
  }

  private def setUpFormPostData(selectedNodes: List[UUID] = List(), allNodes: List[UUID], selectedFolderId: UUID): Seq[(String, String)] = {
    val selected: Seq[(String, String)] = selectedNodes.map(n => ("selected[]", n.toString))
    val all: Seq[(String, String)] = allNodes.map(n => ("allNodes[]", n.toString))
    val other: Seq[(String, String)] = Seq(
      ("pageSelected", "1"),
      ("folderSelected", selectedFolderId.toString))

    all ++ selected ++ other
  }
}
