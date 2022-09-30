package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.AddConsignment.addConsignment
import graphql.codegen.GetConsignment.{getConsignment => gc}
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => gcfe}
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
import graphql.codegen.GetConsignmentFolderDetails.getConsignmentFolderDetails
import graphql.codegen.GetConsignmentFolderDetails.getConsignmentFolderDetails.GetConsignment
import graphql.codegen.GetConsignmentPaginatedFiles.getConsignmentPaginatedFiles.GetConsignment.PaginatedFiles
import graphql.codegen.GetConsignmentPaginatedFiles.getConsignmentPaginatedFiles.GetConsignment.PaginatedFiles.{Edges, PageInfo}
import graphql.codegen.GetConsignmentPaginatedFiles.{getConsignmentPaginatedFiles => gcpf}
import graphql.codegen.GetConsignmentType.{getConsignmentType => gct}
import graphql.codegen.UpdateConsignmentSeriesId.{updateConsignmentSeriesId => ucs}
import graphql.codegen.types.{AddConsignmentInput, FileFilters, PaginationInput}
import org.keycloak.representations.AccessToken
import org.mockito.Mockito
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import sttp.client.HttpError
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentServiceSpec extends AnyWordSpec with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQlConfig = mock[GraphQLConfiguration]
  private val getConsignmentClient = mock[GraphQLClient[gc.Data, gc.Variables]]
  private val addConsignmentClient = mock[GraphQLClient[addConsignment.Data, addConsignment.Variables]]
  private val getConsignmentFolderInfoClient = mock[GraphQLClient[getConsignmentFolderDetails.Data, getConsignmentFolderDetails.Variables]]
  private val getConsignmentTypeClient = mock[GraphQLClient[gct.Data, gct.Variables]]
  private val updateConsignmentSeriesIdClient = mock[GraphQLClient[ucs.Data, ucs.Variables]]
  private val getConsignmentForExportClient = mock[GraphQLClient[gcfe.Data, gcfe.Variables]]
  private val getConsignmentFilesMetadataClient = mock[GraphQLClient[gcfm.Data, gcfm.Variables]]
  private val getConsignmentPaginatedFilesClient = mock[GraphQLClient[gcpf.Data, gcpf.Variables]]
  when(graphQlConfig.getClient[gc.Data, gc.Variables]()).thenReturn(getConsignmentClient)
  when(graphQlConfig.getClient[addConsignment.Data, addConsignment.Variables]()).thenReturn(addConsignmentClient)
  when(graphQlConfig.getClient[getConsignmentFolderDetails.Data, getConsignmentFolderDetails.Variables]()).thenReturn(getConsignmentFolderInfoClient)
  when(graphQlConfig.getClient[gct.Data, gct.Variables]()).thenReturn(getConsignmentTypeClient)
  when(graphQlConfig.getClient[gcfe.Data, gcfe.Variables]()).thenReturn(getConsignmentForExportClient)
  when(graphQlConfig.getClient[gcfm.Data, gcfm.Variables]()).thenReturn(getConsignmentFilesMetadataClient)
  when(graphQlConfig.getClient[gcpf.Data, gcpf.Variables]()).thenReturn(getConsignmentPaginatedFilesClient)

  private val consignmentService = new ConsignmentService(graphQlConfig)

  private val accessToken = new AccessToken()
  private val bearerAccessToken = new BearerAccessToken("some-token")
  private val token = new Token(accessToken, bearerAccessToken)
  private val consignmentId = UUID.fromString("180f9166-fe3c-486e-b9ab-6dfa5f3058dc")
  private val seriesId = Some(UUID.fromString("d54a5118-33a0-4ba2-8030-d16efcf1d1f4"))

  override def afterEach(): Unit = {
    Mockito.reset(getConsignmentClient)
    Mockito.reset(addConsignmentClient)
    Mockito.reset(getConsignmentTypeClient)
  }

  "consignmentExists" should {
    "return true when given a valid consignment id" in {
      val response = GraphQlResponse(Some(gc.Data(Some(gc.GetConsignment(consignmentId, seriesId, None, "ref", None)))), Nil)
      when(getConsignmentClient.getResult(bearerAccessToken, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignment = consignmentService.consignmentExists(consignmentId, bearerAccessToken)
      val actualResults = getConsignment.futureValue

      actualResults should be(true)
    }

    "return false if consignment with given id does not exist" in {
      val response = GraphQlResponse(Some(gc.Data(None)), Nil)
      when(getConsignmentClient.getResult(bearerAccessToken, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignment = consignmentService.consignmentExists(consignmentId, bearerAccessToken)
      val actualResults = getConsignment.futureValue

      actualResults should be(false)
    }

    "return an error when the API has an error" in {
      when(getConsignmentClient.getResult(bearerAccessToken, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val results = consignmentService.consignmentExists(consignmentId, bearerAccessToken)
      results.failed.futureValue shouldBe a[HttpError]
    }

    "throw an AuthorisationException if the API returns an auth error" in {
      val response = GraphQlResponse[gc.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
      when(getConsignmentClient.getResult(bearerAccessToken, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignment = consignmentService.consignmentExists(consignmentId, bearerAccessToken)
      val results = getConsignment.failed.futureValue
      results shouldBe a[AuthorisationException]
    }
  }

  "createConsignment" should {
    "create a consignment of type 'standard' with the given series when no user type provided" in {
      val noUserTypeToken = mock[Token]
      when(noUserTypeToken.bearerAccessToken).thenReturn(bearerAccessToken)

      val response = GraphQlResponse(Some(new addConsignment.Data(addConsignment.AddConsignment(Some(consignmentId), seriesId))), Nil)
      val expectedVariables = Some(addConsignment.Variables(AddConsignmentInput(seriesId, "standard")))
      when(addConsignmentClient.getResult(bearerAccessToken, addConsignment.document, expectedVariables))
        .thenReturn(Future.successful(response))

      consignmentService.createConsignment(seriesId, token)

      Mockito.verify(addConsignmentClient).getResult(bearerAccessToken, addConsignment.document, expectedVariables)
    }

    "create a consignment of type 'standard' with the given series when standard user type provided" in {
      val standardUserToken: Token = mock[Token]
      when(standardUserToken.isStandardUser).thenReturn(true)
      when(standardUserToken.bearerAccessToken).thenReturn(bearerAccessToken)

      val response = GraphQlResponse(Some(new addConsignment.Data(addConsignment.AddConsignment(Some(consignmentId), seriesId))), Nil)
      val expectedVariables = Some(addConsignment.Variables(AddConsignmentInput(seriesId, "standard")))
      when(addConsignmentClient.getResult(bearerAccessToken, addConsignment.document, expectedVariables))
        .thenReturn(Future.successful(response))

      consignmentService.createConsignment(seriesId, standardUserToken)

      Mockito.verify(addConsignmentClient).getResult(bearerAccessToken, addConsignment.document, expectedVariables)
    }

    "create a consignment of type 'judgment' when judgment user type provided" in {
      val judgmentUserToken: Token = mock[Token]
      when(judgmentUserToken.isJudgmentUser).thenReturn(true)
      when(judgmentUserToken.bearerAccessToken).thenReturn(bearerAccessToken)
      val response = GraphQlResponse(Some(new addConsignment.Data(addConsignment.AddConsignment(Some(consignmentId), None))), Nil)
      val expectedVariables = Some(addConsignment.Variables(AddConsignmentInput(None, "judgment")))
      when(addConsignmentClient.getResult(bearerAccessToken, addConsignment.document, expectedVariables))
        .thenReturn(Future.successful(response))

      consignmentService.createConsignment(None, judgmentUserToken)

      Mockito.verify(addConsignmentClient).getResult(bearerAccessToken, addConsignment.document, expectedVariables)
    }

    "return the created consignment" in {
      val response = GraphQlResponse(Some(new addConsignment.Data(addConsignment.AddConsignment(Some(consignmentId), seriesId))), Nil)
      when(addConsignmentClient.getResult(
        bearerAccessToken, addConsignment.document, Some(addConsignment.Variables(AddConsignmentInput(seriesId, "standard")))))
        .thenReturn(Future.successful(response))

      val result = consignmentService.createConsignment(seriesId, token).futureValue

      result.consignmentid should contain(consignmentId)
    }

    "return an error when the API has an error" in {
      when(addConsignmentClient.getResult(
        bearerAccessToken, addConsignment.document, Some(addConsignment.Variables(AddConsignmentInput(seriesId, "standard")))))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val results = consignmentService.createConsignment(seriesId, token)

      results.failed.futureValue shouldBe a[HttpError]
    }

    "throw an AuthorisationException if the API returns an auth error" in {
      val response = GraphQlResponse[addConsignment.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
      when(addConsignmentClient.getResult(
        bearerAccessToken, addConsignment.document, Some(addConsignment.Variables(AddConsignmentInput(seriesId, "standard")))))
        .thenReturn(Future.successful(response))

      val results = consignmentService.createConsignment(seriesId, token).failed.futureValue
      results shouldBe a[AuthorisationException]
    }
  }

  "getConsignmentFolderInfo" should {
    "return information about a consignment when given a consignment id" in {
      val response = GraphQlResponse[getConsignmentFolderDetails
      .Data](Some(getConsignmentFolderDetails
        .Data(Some(getConsignmentFolderDetails
          .GetConsignment(3, Some("Test Parent Folder"))))), Nil)

      when(getConsignmentFolderInfoClient.getResult(
        bearerAccessToken, getConsignmentFolderDetails.document, Some(getConsignmentFolderDetails.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignmentDetails = consignmentService.getConsignmentFolderInfo(consignmentId, bearerAccessToken).futureValue

      getConsignmentDetails should be(GetConsignment(3, Some("Test Parent Folder")))
    }
  }

  "return an error if the API returns an error" in {
    when(getConsignmentFolderInfoClient.getResult(
      bearerAccessToken, getConsignmentFolderDetails.document, Some(getConsignmentFolderDetails.Variables(consignmentId))))
      .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

    val getConsignmentDetails = consignmentService.getConsignmentFolderInfo(consignmentId, bearerAccessToken).failed.futureValue

    getConsignmentDetails shouldBe a[HttpError]
  }

  "return an empty object if there are no consignment details" in {
    val response = GraphQlResponse[getConsignmentFolderDetails
    .Data](Some(getConsignmentFolderDetails
      .Data(Some(getConsignmentFolderDetails
        .GetConsignment(0, None)))), Nil)

    when(getConsignmentFolderInfoClient.getResult(
      bearerAccessToken, getConsignmentFolderDetails.document, Some(getConsignmentFolderDetails.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val getConsignmentDetails = consignmentService.getConsignmentFolderInfo(consignmentId, bearerAccessToken).futureValue

    getConsignmentDetails should be(GetConsignment(0, None))
  }

  "getConsignmentFileMetadata" should {
    "return consignment with file metadata when consignment id and selected file ids are passed" in {
      val fileId = UUID.randomUUID()
      val exemptionCode = Some("Open")
      val graphQlGetConsignmentFilesMetadata = gcfm.GetConsignment(
        List(gcfm.GetConsignment.Files(fileId, Nil, gcfm.GetConsignment.Files.Metadata(exemptionCode, None, None, None, None))), "TEST-TDR-2021-GB")

      val response = GraphQlResponse[gcfm.Data](Some(gcfm.Data(Some(graphQlGetConsignmentFilesMetadata))), Nil)

      val selectedFileIds = Option(List(fileId))
      val fileFilters = Option(FileFilters(None, selectedFileIds, None))
      when(getConsignmentFilesMetadataClient.getResult(bearerAccessToken, gcfm.document,
        Some(gcfm.Variables(consignmentId, Some(FileFilters(None, selectedFileIds, None))))))
        .thenReturn(Future.successful(response))

      val getConsignmentDetails = consignmentService.getConsignmentFileMetadata(consignmentId, bearerAccessToken, fileFilters).futureValue

      getConsignmentDetails.files.size should be(1)
      getConsignmentDetails.files.head.metadata.foiExemptionCode should be(exemptionCode)
    }

    "return consignment with file metadata when only consignment id is passed" in {
      val fileId = UUID.randomUUID()
      val exemptionCode = Some("Open")
      val graphQlGetConsignmentFilesMetadata = gcfm.GetConsignment(
        List(gcfm.GetConsignment.Files(fileId, Nil, gcfm.GetConsignment.Files.Metadata(exemptionCode, None, None, None, None))), "TEST-TDR-2021-GB")

      val response = GraphQlResponse[gcfm.Data](Some(gcfm.Data(Some(graphQlGetConsignmentFilesMetadata))), Nil)

      when(getConsignmentFilesMetadataClient.getResult(bearerAccessToken, gcfm.document,
        Some(gcfm.Variables(consignmentId, None))))
        .thenReturn(Future.successful(response))

      val getConsignmentDetails = consignmentService.getConsignmentFileMetadata(consignmentId, bearerAccessToken).futureValue

      getConsignmentDetails.files.size should be(1)
      getConsignmentDetails.files.head.metadata.foiExemptionCode should be(exemptionCode)
    }

    "raise an exception if given consignment id does not exist" in {
      val response = GraphQlResponse(Some(gcfm.Data(None)), Nil)

      when(getConsignmentFilesMetadataClient.getResult(bearerAccessToken, gcfm.document,
        Some(gcfm.Variables(consignmentId, None))))
        .thenReturn(Future.successful(response))

      val results = consignmentService.getConsignmentFileMetadata(consignmentId, bearerAccessToken)

      results.failed.futureValue shouldBe a[IllegalStateException]
      results.failed.futureValue.getMessage shouldBe s"No consignment found for consignment $consignmentId"
    }

    "return an error when the API has an error" in {
      when(getConsignmentFilesMetadataClient.getResult(bearerAccessToken, gcfm.document,
        Some(gcfm.Variables(consignmentId, None))))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val results = consignmentService.getConsignmentFileMetadata(consignmentId, bearerAccessToken)
      results.failed.futureValue shouldBe a[HttpError]
    }
  }

  "getConsignmentType" should {
    def mockGraphqlResponse(consignmentType: String): OngoingStubbing[Future[GraphQlResponse[gct.Data]]] = {
      val response = GraphQlResponse(
        Some(gct.Data(Some(gct.GetConsignment(Some(consignmentType)))))
        , List())
      when(getConsignmentTypeClient.getResult(bearerAccessToken, gct.document, Some(gct.Variables(consignmentId))))
        .thenReturn(Future.successful(response))
    }

    def mockErrorResponse: OngoingStubbing[Future[GraphQlResponse[gct.Data]]] =
      when(getConsignmentTypeClient.getResult(bearerAccessToken, gct.document, Some(gct.Variables(consignmentId))))
      .thenReturn(Future.successful(GraphQlResponse(None, List(NotAuthorisedError("error", Nil, Nil)))))

    def mockMissingConsignmentType: OngoingStubbing[Future[GraphQlResponse[gct.Data]]] =
      when(getConsignmentTypeClient.getResult(bearerAccessToken, gct.document, Some(gct.Variables(consignmentId))))
      .thenReturn(Future.successful(GraphQlResponse(Some(gct.Data(Some(gct.GetConsignment(None)))), List())))

    def mockAPIFailedResponse: OngoingStubbing[Future[GraphQlResponse[gct.Data]]] =
      when(getConsignmentTypeClient.getResult(bearerAccessToken, gct.document, Some(gct.Variables(consignmentId))))
      .thenReturn(Future.failed(new Exception("API failure")))

    "return the correct consignment type for a judgment type" in {
      val expectedType = "judgment"
      mockGraphqlResponse(expectedType)
      val consignmentType = consignmentService.getConsignmentType(consignmentId, bearerAccessToken).futureValue
      consignmentType should be("judgment")
    }

    "return the correct consignment type for a standard type" in {
      val expectedType = "standard"
      mockGraphqlResponse(expectedType)
      val consignmentType = consignmentService.getConsignmentType(consignmentId, bearerAccessToken).futureValue
      consignmentType should be(expectedType)
    }

    "return the an error if there is an error from the API" in {
      mockErrorResponse
      val error = consignmentService.getConsignmentType(consignmentId, bearerAccessToken).failed.futureValue
      error.getMessage should be("error")
    }

    "return the an error if the API fails" in {
      mockAPIFailedResponse
      val error = consignmentService.getConsignmentType(consignmentId, bearerAccessToken).failed.futureValue
      error.getMessage should be("API failure")
    }

    "return an error if the consignment type is missing" in {
      mockMissingConsignmentType
      val error = consignmentService.getConsignmentType(consignmentId, bearerAccessToken).failed.futureValue
      error.getMessage should be(s"No consignment type found for consignment $consignmentId")
    }
  }

  "getConsignmentExport" should {
    "return export information about a consignment when given a consignment id" in {
      val fileId = UUID.randomUUID()
      val filename = "filename"
      val filetype = Some("File")
      val filepath = "filepath"
      val exemptionCode = Some("Open")
      val heldBy = Some("TNA")
      val language = Some("English")
      val legalStatus = Some("Public Record")
      val rightsCopyright = Some("Crown Copyright")
      val graphQlExportData = gcfe.GetConsignment(consignmentId, None, None, None, "TEST-TDR-2021-GB", None, None, None,
        List(gcfe.GetConsignment.Files(fileId, filetype, fileName = Some(filename), None,
          metadata = gcfe.GetConsignment.Files.Metadata(None, None, clientSideOriginalFilePath = Some(s"$filepath/$filename"),
            exemptionCode, heldBy, language, legalStatus, rightsCopyright, None),
          ffidMetadata = None, antivirusMetadata = None))
      )
      val response = GraphQlResponse(
        Some(gcfe.Data(Some(graphQlExportData))),
        Nil)

      when(getConsignmentForExportClient.getResult(bearerAccessToken, gcfe.document, Some(gcfe.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignmentExport = consignmentService.getConsignmentExport(consignmentId, bearerAccessToken)
      val actualResults = getConsignmentExport.futureValue

      actualResults should be(graphQlExportData)
    }

    "return an error if the API fails" in {
      when(getConsignmentForExportClient.getResult(bearerAccessToken, gcfe.document, Some(gcfe.Variables(consignmentId))))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val getConsignmentExport = consignmentService.getConsignmentExport(consignmentId, bearerAccessToken).failed.futureValue

      getConsignmentExport shouldBe a[HttpError]
    }

    "return an error if there is an error from the API" in {
      when(getConsignmentForExportClient.getResult(bearerAccessToken, gcfe.document, Some(gcfe.Variables(consignmentId))))
        .thenReturn(Future.successful(GraphQlResponse(None, List(NotAuthorisedError("error", Nil, Nil)))))

      val getConsignmentExport = consignmentService.getConsignmentExport(consignmentId, bearerAccessToken).failed.futureValue

      getConsignmentExport.getMessage should be("error")
    }
  }

  "getConsignmentPaginatedFile" should {
    "return paginated file information for a consignment given a folderId" in {
      val fileId = UUID.randomUUID()
      val folderId = UUID.randomUUID()
      val parentFolderName = Some("ParentFolder")
      val limit = Some(1)
      val page = 1
      val paginatedFiles: gcpf.GetConsignment.PaginatedFiles =
        PaginatedFiles(PageInfo(startCursor = None, endCursor = None, hasNextPage = true, hasPreviousPage = false),
          Some(List(
            Some(Edges(Edges.Node(fileId = folderId, fileName = parentFolderName, fileType = Some("Folder"), parentId = None))),
            Some(Edges(Edges.Node(fileId = fileId, fileName = Some("FileName"), fileType = Some("File"), parentId = Some(folderId)))))),
          totalPages = Some(1), totalItems = Some(1))

      val graphQlPaginatedData = gcpf.GetConsignment(parentFolder = parentFolderName, parentFolderId = Some(folderId), paginatedFiles = paginatedFiles)
      val response = GraphQlResponse(Some(gcpf.Data(Some(graphQlPaginatedData))), Nil)

      when(getConsignmentPaginatedFilesClient.getResult(bearerAccessToken, gcpf.document, Some(gcpf.Variables(consignmentId,
        Some(PaginationInput(limit, currentPage = Some(page), currentCursor = None, Some(FileFilters(None, None, Some(folderId))))))))
      ).thenReturn(Future.successful(response))

      val getConsignmentPaginated = consignmentService.getConsignmentPaginatedFile(consignmentId, page, limit, selectedFolderId = folderId, bearerAccessToken)
      val actualResults = getConsignmentPaginated.futureValue

      actualResults should be(graphQlPaginatedData)
    }

    "return an error if there is an error from the API" in {
      val folderId = UUID.randomUUID()
      val limit = Some(1)
      val page = 1
      when(getConsignmentPaginatedFilesClient.getResult(bearerAccessToken, gcpf.document,
        Some(gcpf.Variables(consignmentId,
          Some(PaginationInput(limit, currentPage = Some(page), currentCursor = None, Some(FileFilters(None, None, Some(folderId)))))))))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val results = consignmentService.getConsignmentPaginatedFile(
        consignmentId, page = page, limit = limit, selectedFolderId = folderId, bearerAccessToken)
      results.failed.futureValue shouldBe a[HttpError]
    }
  }
}
