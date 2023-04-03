package services

import cats.implicits.catsSyntaxOptionId
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.AddConsignment.addConsignment
import graphql.codegen.GetConsignment.{getConsignment => gc}
import graphql.codegen.GetConsignmentExport.{getConsignmentForExport => gcfe}
import graphql.codegen.GetConsignmentFiles.getConsignmentFiles.GetConsignment.Files.FileStatuses
import graphql.codegen.GetConsignmentFiles.{getConsignmentFiles => gcf}
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
import graphql.codegen.GetConsignmentFolderDetails.getConsignmentFolderDetails
import graphql.codegen.GetConsignmentFolderDetails.getConsignmentFolderDetails.GetConsignment
import graphql.codegen.GetConsignmentPaginatedFiles.{getConsignmentPaginatedFiles => gcpf}
import graphql.codegen.GetConsignmentType.{getConsignmentType => gct}
import graphql.codegen.GetConsignments.getConsignments.Consignments
import graphql.codegen.GetConsignments.getConsignments.Consignments.Edges.Node
import graphql.codegen.GetConsignments.{getConsignments => gcs}
import graphql.codegen.types.{AddConsignmentInput, ConsignmentFilters, FileFilters, FileMetadataFilters}
import org.keycloak.representations.AccessToken
import org.mockito.Mockito
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import org.scalatest.prop.TableFor1
import org.scalatest.prop.Tables.Table
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import services.ConsignmentService.StatusTag
import sttp.client.HttpError
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.time.ZonedDateTime
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentServiceSpec extends AnyWordSpec with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQlConfig = mock[GraphQLConfiguration]
  private val getConsignmentClient = mock[GraphQLClient[gc.Data, gc.Variables]]
  private val addConsignmentClient = mock[GraphQLClient[addConsignment.Data, addConsignment.Variables]]
  private val getConsignmentFolderInfoClient = mock[GraphQLClient[getConsignmentFolderDetails.Data, getConsignmentFolderDetails.Variables]]
  private val getConsignmentTypeClient = mock[GraphQLClient[gct.Data, gct.Variables]]
  private val getConsignmentForExportClient = mock[GraphQLClient[gcfe.Data, gcfe.Variables]]
  private val getConsignmentFilesMetadataClient = mock[GraphQLClient[gcfm.Data, gcfm.Variables]]
  private val getConsignmentPaginatedFilesClient = mock[GraphQLClient[gcpf.Data, gcpf.Variables]]
  private val getConsignmentFilesClient = mock[GraphQLClient[gcf.Data, gcf.Variables]]
  private val getConsignmentsClient = mock[GraphQLClient[gcs.Data, gcs.Variables]]
  when(graphQlConfig.getClient[gc.Data, gc.Variables]()).thenReturn(getConsignmentClient)
  when(graphQlConfig.getClient[addConsignment.Data, addConsignment.Variables]()).thenReturn(addConsignmentClient)
  when(graphQlConfig.getClient[getConsignmentFolderDetails.Data, getConsignmentFolderDetails.Variables]()).thenReturn(getConsignmentFolderInfoClient)
  when(graphQlConfig.getClient[gct.Data, gct.Variables]()).thenReturn(getConsignmentTypeClient)
  when(graphQlConfig.getClient[gcfe.Data, gcfe.Variables]()).thenReturn(getConsignmentForExportClient)
  when(graphQlConfig.getClient[gcfm.Data, gcfm.Variables]()).thenReturn(getConsignmentFilesMetadataClient)
  when(graphQlConfig.getClient[gcpf.Data, gcpf.Variables]()).thenReturn(getConsignmentPaginatedFilesClient)
  when(graphQlConfig.getClient[gcf.Data, gcf.Variables]()).thenReturn(getConsignmentFilesClient)
  when(graphQlConfig.getClient[gcs.Data, gcs.Variables]()).thenReturn(getConsignmentsClient)

  private val consignmentService = new ConsignmentService(graphQlConfig)

  private val accessToken = new AccessToken()
  private val bearerAccessToken = new BearerAccessToken("some-token")
  private val token = new Token(accessToken, bearerAccessToken)
  private val consignmentId = UUID.fromString("180f9166-fe3c-486e-b9ab-6dfa5f3058dc")
  private val seriesId = Some(UUID.fromString("d54a5118-33a0-4ba2-8030-d16efcf1d1f4"))

  def generateMetadata(numberOfFiles: Int, fileType: String, closureType: String): List[gcfm.GetConsignment.Files] = {
    List
      .fill(numberOfFiles)(UUID.randomUUID())
      .map(fileId => {
        val fileMetadata = List(gcfm.GetConsignment.Files.FileMetadata("FileType", fileType), gcfm.GetConsignment.Files.FileMetadata("ClosureType", closureType))
        gcfm.GetConsignment.Files(fileId, Some("FileName"), fileMetadata, Nil)
      })
  }

  override def afterEach(): Unit = {
    Mockito.reset(getConsignmentClient)
    Mockito.reset(addConsignmentClient)
    Mockito.reset(getConsignmentTypeClient)
  }

  "consignmentExists" should {
    "return true when given a valid consignment id" in {
      val response = GraphQlResponse(Some(gc.Data(Some(gc.GetConsignment(consignmentId, seriesId, None, "ref", None, Nil)))), Nil)
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
      when(addConsignmentClient.getResult(bearerAccessToken, addConsignment.document, Some(addConsignment.Variables(AddConsignmentInput(seriesId, "standard")))))
        .thenReturn(Future.successful(response))

      val result = consignmentService.createConsignment(seriesId, token).futureValue

      result.consignmentid should contain(consignmentId)
    }

    "return an error when the API has an error" in {
      when(addConsignmentClient.getResult(bearerAccessToken, addConsignment.document, Some(addConsignment.Variables(AddConsignmentInput(seriesId, "standard")))))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val results = consignmentService.createConsignment(seriesId, token)

      results.failed.futureValue shouldBe a[HttpError]
    }

    "throw an AuthorisationException if the API returns an auth error" in {
      val response = GraphQlResponse[addConsignment.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
      when(addConsignmentClient.getResult(bearerAccessToken, addConsignment.document, Some(addConsignment.Variables(AddConsignmentInput(seriesId, "standard")))))
        .thenReturn(Future.successful(response))

      val results = consignmentService.createConsignment(seriesId, token).failed.futureValue
      results shouldBe a[AuthorisationException]
    }
  }

  "getConsignmentFolderInfo" should {
    "return information about a consignment when given a consignment id" in {
      val response = GraphQlResponse[getConsignmentFolderDetails.Data](
        Some(
          getConsignmentFolderDetails
            .Data(
              Some(
                getConsignmentFolderDetails
                  .GetConsignment(3, Some("Test Parent Folder"))
              )
            )
        ),
        Nil
      )

      when(getConsignmentFolderInfoClient.getResult(bearerAccessToken, getConsignmentFolderDetails.document, Some(getConsignmentFolderDetails.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignmentDetails = consignmentService.getConsignmentFolderInfo(consignmentId, bearerAccessToken).futureValue

      getConsignmentDetails should be(GetConsignment(3, Some("Test Parent Folder")))
    }
  }

  "return an error if the API returns an error" in {
    when(getConsignmentFolderInfoClient.getResult(bearerAccessToken, getConsignmentFolderDetails.document, Some(getConsignmentFolderDetails.Variables(consignmentId))))
      .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

    val getConsignmentDetails = consignmentService.getConsignmentFolderInfo(consignmentId, bearerAccessToken).failed.futureValue

    getConsignmentDetails shouldBe a[HttpError]
  }

  "return an empty object if there are no consignment details" in {
    val response = GraphQlResponse[getConsignmentFolderDetails.Data](
      Some(
        getConsignmentFolderDetails
          .Data(
            Some(
              getConsignmentFolderDetails
                .GetConsignment(0, None)
            )
          )
      ),
      Nil
    )

    when(getConsignmentFolderInfoClient.getResult(bearerAccessToken, getConsignmentFolderDetails.document, Some(getConsignmentFolderDetails.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val getConsignmentDetails = consignmentService.getConsignmentFolderInfo(consignmentId, bearerAccessToken).futureValue

    getConsignmentDetails should be(GetConsignment(0, None))
  }

  "getConsignmentFileMetadata" should {
    "return consignment with file metadata when consignment id and selected file ids are passed" in {
      val fileId = UUID.randomUUID()
      val exemptionCode = "Open"
      val graphQlGetConsignmentFilesMetadata =
        gcfm.GetConsignment(
          List(gcfm.GetConsignment.Files(fileId, Some("FileName"), List(gcfm.GetConsignment.Files.FileMetadata("FoiExemptionCode", "Open")), Nil)),
          "TEST-TDR-2021-GB"
        )

      val response = GraphQlResponse[gcfm.Data](Some(gcfm.Data(Some(graphQlGetConsignmentFilesMetadata))), Nil)

      val selectedFileIds = Option(List(fileId))
      val fileFilters = Option(FileFilters(Some("File"), selectedFileIds, None, None))
      when(getConsignmentFilesMetadataClient.getResult(bearerAccessToken, gcfm.document, Some(gcfm.Variables(consignmentId, fileFilters))))
        .thenReturn(Future.successful(response))

      val getConsignmentDetails = consignmentService.getConsignmentFileMetadata(consignmentId, bearerAccessToken, None, selectedFileIds, None).futureValue

      getConsignmentDetails.files.size should be(1)
      getConsignmentDetails.files.head.fileMetadata.head.value should be(exemptionCode)
    }

    "return consignment with file metadata when only consignment id and additionalProperties are passed" in {
      val fileId = UUID.randomUUID()
      val exemptionCode = "Open"
      val graphQlGetConsignmentFilesMetadata =
        gcfm.GetConsignment(List(gcfm.GetConsignment.Files(fileId, Some("FileName"), List(gcfm.GetConsignment.Files.FileMetadata("", exemptionCode)), Nil)), "TEST-TDR-2021-GB")

      val response = GraphQlResponse[gcfm.Data](Some(gcfm.Data(Some(graphQlGetConsignmentFilesMetadata))), Nil)

      val additionalProperties = Some(List("property1"))
      val fileFilter = FileFilters("File".some, None, None, FileMetadataFilters(None, None, additionalProperties).some).some
      when(getConsignmentFilesMetadataClient.getResult(bearerAccessToken, gcfm.document, Some(gcfm.Variables(consignmentId, fileFilter))))
        .thenReturn(Future.successful(response))

      val getConsignmentDetails = consignmentService.getConsignmentFileMetadata(consignmentId, bearerAccessToken, None, None, additionalProperties).futureValue

      getConsignmentDetails.files.size should be(1)
      getConsignmentDetails.files.head.fileMetadata.head.value should be(exemptionCode)
    }

    "return consignment with closure metadata when metadata type is 'closure', fileIds and additionalProperties are passed" in {
      val fileId = UUID.randomUUID()
      val exemptionCode = "Open"
      val graphQlGetConsignmentFilesMetadata =
        gcfm.GetConsignment(List(gcfm.GetConsignment.Files(fileId, Some("FileName"), List(gcfm.GetConsignment.Files.FileMetadata("", exemptionCode)), Nil)), "TEST-TDR-2021-GB")

      val response = GraphQlResponse[gcfm.Data](Some(gcfm.Data(Some(graphQlGetConsignmentFilesMetadata))), Nil)

      val additionalProperties = Some(List("property1"))
      val fileFilter = FileFilters("File".some, List(fileId).some, None, FileMetadataFilters(Some(true), None, additionalProperties).some).some
      when(getConsignmentFilesMetadataClient.getResult(bearerAccessToken, gcfm.document, Some(gcfm.Variables(consignmentId, fileFilter))))
        .thenReturn(Future.successful(response))

      val getConsignmentDetails =
        consignmentService.getConsignmentFileMetadata(consignmentId, bearerAccessToken, "closure".some, List(fileId).some, additionalProperties).futureValue

      getConsignmentDetails.files.size should be(1)
      getConsignmentDetails.files.head.fileMetadata.head.value should be(exemptionCode)
    }

    "return consignment with descriptive metadata when metadata type as 'descriptive' passed" in {
      val fileId = UUID.randomUUID()
      val exemptionCode = "Open"
      val graphQlGetConsignmentFilesMetadata =
        gcfm.GetConsignment(List(gcfm.GetConsignment.Files(fileId, Some("FileName"), List(gcfm.GetConsignment.Files.FileMetadata("", exemptionCode)), Nil)), "TEST-TDR-2021-GB")

      val response = GraphQlResponse[gcfm.Data](Some(gcfm.Data(Some(graphQlGetConsignmentFilesMetadata))), Nil)

      val additionalProperties = Some(List("property1"))
      val fileFilter = FileFilters("File".some, None, None, FileMetadataFilters(None, Some(true), additionalProperties).some).some
      when(getConsignmentFilesMetadataClient.getResult(bearerAccessToken, gcfm.document, Some(gcfm.Variables(consignmentId, fileFilter))))
        .thenReturn(Future.successful(response))

      val getConsignmentDetails = consignmentService.getConsignmentFileMetadata(consignmentId, bearerAccessToken, "descriptive".some, None, additionalProperties).futureValue

      getConsignmentDetails.files.size should be(1)
      getConsignmentDetails.files.head.fileMetadata.head.value should be(exemptionCode)
    }

    "raise an exception if given metadata type is not valid" in {
      val thrownException =
        the[IllegalArgumentException] thrownBy consignmentService.getConsignmentFileMetadata(consignmentId, bearerAccessToken, "invalidMetadataType".some, None, None)

      thrownException.getMessage should equal("Invalid metadata type: invalidMetadataType")
    }

    "raise an exception if given consignment id does not exist" in {
      val response = GraphQlResponse(Some(gcfm.Data(None)), Nil)

      when(getConsignmentFilesMetadataClient.getResult(bearerAccessToken, gcfm.document, Some(gcfm.Variables(consignmentId, FileFilters("File".some, None, None, None).some))))
        .thenReturn(Future.successful(response))

      val results = consignmentService.getConsignmentFileMetadata(consignmentId, bearerAccessToken, None, None, None)

      results.failed.futureValue shouldBe a[IllegalStateException]
      results.failed.futureValue.getMessage shouldBe s"No consignment found for consignment $consignmentId"
    }

    "return an error when the API has an error" in {
      when(getConsignmentFilesMetadataClient.getResult(bearerAccessToken, gcfm.document, Some(gcfm.Variables(consignmentId, FileFilters("File".some, None, None, None).some))))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val results = consignmentService.getConsignmentFileMetadata(consignmentId, bearerAccessToken, None, None, None)
      results.failed.futureValue shouldBe a[HttpError]
    }
  }

  "getConsignmentType" should {
    def mockGraphqlResponse(consignmentType: String): OngoingStubbing[Future[GraphQlResponse[gct.Data]]] = {
      val response = GraphQlResponse(Some(gct.Data(Some(gct.GetConsignment(Some(consignmentType))))), List())
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

  val metadataType: TableFor1[String] = Table(
    "Metadata Type",
    "closure",
    "descriptive"
  )

  "getAllConsignmentFiles" should {
    forAll(metadataType) { metadataType =>
      s"return nested files correctly for $metadataType" in {
        val parentId = UUID.randomUUID()
        val descendantOneId = UUID.randomUUID()
        val descendantTwoId = UUID.randomUUID()
        val parentFile = gcf.GetConsignment.Files(parentId, Option("parent"), Option("File"), None, gcf.GetConsignment.Files.Metadata(None), Nil)
        val descendantOne = gcf.GetConsignment.Files(
          descendantOneId,
          Option("descendantOne"),
          Option("File"),
          Option(parentId),
          gcf.GetConsignment.Files.Metadata(None),
          List(FileStatuses("ClosureMetadata", "Incomplete"), FileStatuses("DescriptiveMetadata", "NotEntered"))
        )
        val descendantTwo = gcf.GetConsignment.Files(
          descendantTwoId,
          Option("descendantTwo"),
          Option("File"),
          Option(descendantOneId),
          gcf.GetConsignment.Files.Metadata(None),
          List(FileStatuses("ClosureMetadata", "Completed"), FileStatuses("DescriptiveMetadata", "Completed"))
        )
        val files = gcf.GetConsignment(List(parentFile, descendantOne, descendantTwo))
        val response = GraphQlResponse(Some(gcf.Data(Some(files))), Nil)
        when(
          getConsignmentFilesClient
            .getResult(bearerAccessToken, gcf.document, Some(gcf.Variables(consignmentId)))
        )
          .thenReturn(Future.successful(response))
        val result: ConsignmentService.File = consignmentService.getAllConsignmentFiles(consignmentId, bearerAccessToken, metadataType).futureValue

        val expectedStatusTagForFirst = Map("closure" -> StatusTag("incomplete", "red").some, "descriptive" -> None)
        val expectedStatusTagForSecond = Map("closure" -> StatusTag("closed", "blue").some, "descriptive" -> StatusTag("entered", "blue").some)
        result.id should equal(parentId)
        result.children.length should equal(1)
        val firstDescendant = result.children.head
        firstDescendant.id should equal(descendantOneId)
        firstDescendant.statusTag should equal(expectedStatusTagForFirst(metadataType))
        firstDescendant.children.length should equal(1)

        val secondDescendant = firstDescendant.children.head
        secondDescendant.id should equal(descendantTwoId)
        secondDescendant.statusTag should equal(expectedStatusTagForSecond(metadataType))
        secondDescendant.children.length should equal(0)
      }
    }

    "throw an error if the parent folder is missing" in {
      val parentId = UUID.randomUUID()
      val descendantOne = gcf.GetConsignment.Files(UUID.randomUUID(), Option("descendantOne"), Option("File"), Option(parentId), gcf.GetConsignment.Files.Metadata(None), Nil)
      val files = gcf.GetConsignment(List(descendantOne))
      val response = GraphQlResponse(Some(gcf.Data(Some(files))), Nil)
      when(
        getConsignmentFilesClient
          .getResult(bearerAccessToken, gcf.document, Some(gcf.Variables(consignmentId)))
      )
        .thenReturn(Future.successful(response))

      val error = consignmentService.getAllConsignmentFiles(consignmentId, bearerAccessToken, metadataType(1)).failed.futureValue
      error.getMessage should equal(s"Parent ID not found for consignment $consignmentId")
    }
  }

  "getConsignments" should {
    "return a list of the user's consignments" in {
      val userId = UUID.randomUUID()

      val edges = List(
        Consignments
          .Edges(
            Node(
              UUID.randomUUID().some,
              "TEST-TDR-2021-GB",
              Some("standard"),
              Some(ZonedDateTime.now()),
              Some(ZonedDateTime.now()),
              List(),
              5
            ),
            "Cursor"
          )
          .some
      )

      val consignments = gcs.Consignments(edges.some, Consignments.PageInfo(hasNextPage = false, None), None)

      val response = GraphQlResponse[gcs.Data](Some(gcs.Data(consignments)), Nil)

      val consignmentFilter = ConsignmentFilters(userId.some, None)
      when(getConsignmentsClient.getResult(bearerAccessToken, gcs.document, gcs.Variables(100, None, Some(1), consignmentFilter.some).some))
        .thenReturn(Future.successful(response))

      val history = consignmentService.getConsignments(1, 100, consignmentFilter, bearerAccessToken).futureValue
      history.edges.get should be(edges)
    }

    "return an error when the API has an error" in {

      val consignmentFilter = ConsignmentFilters(UUID.randomUUID().some, None)
      when(getConsignmentsClient.getResult(bearerAccessToken, gcs.document, gcs.Variables(100, None, Some(1), consignmentFilter.some).some))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val results = consignmentService.getConsignments(1, 100, consignmentFilter, bearerAccessToken)
      results.failed.futureValue shouldBe a[HttpError]
    }
  }

  "areAllFilesClosed" should {
    "return true if all files have a closure type of Closed" in {
      val files = generateMetadata(2, "File", "Closed")
      consignmentService.areAllFilesClosed(gcfm.GetConsignment(files, "")) should equal(true)
    }

    "return true if all files are closed but folders are open" in {
      val files = generateMetadata(2, "File", "Closed")
      val folders = generateMetadata(2, "Folder", "Open")
      val consignment = gcfm.GetConsignment(files ++ folders, "")
      consignmentService.areAllFilesClosed(consignment) should equal(true)
    }

    "return true if no files are provided" in {
      consignmentService.areAllFilesClosed(gcfm.GetConsignment(Nil, "")) should equal(true)
    }

    "return false if one file is open and the others closed" in {
      val closedFile = generateMetadata(1, "File", "Open")
      val openFiles = generateMetadata(3, "File", "Closed")
      val consignment = gcfm.GetConsignment(closedFile ++ openFiles, "")
      consignmentService.areAllFilesClosed(consignment) should equal(false)
    }

    "return false if all files are open" in {
      val files = generateMetadata(3, "File", "Open")
      consignmentService.areAllFilesClosed(gcfm.GetConsignment(files, "")) should equal(false)
    }
  }
}
