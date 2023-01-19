//package services
//
//import com.nimbusds.oauth2.sdk.token.BearerAccessToken
//import configuration.GraphQLBackend.backend
//import configuration.GraphQLConfiguration
//import errors.AuthorisationException
//import graphql.codegen.AddBulkFileMetadata.{addBulkFileMetadata => abfm}
//import graphql.codegen.DeleteFileMetadata.{deleteFileMetadata => dfm}
//import graphql.codegen.GetCustomMetadata.{customMetadata => cm}
//import graphql.codegen.types.DataType.Text
//import graphql.codegen.types.PropertyType.Defined
//import graphql.codegen.types.{DeleteFileMetadataInput, UpdateBulkFileMetadataInput, UpdateFileMetadataInput}
//import org.mockito.Mockito
//import org.mockito.Mockito.{times, verify, when}
//import org.scalatest.BeforeAndAfterEach
//import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
//import org.scalatest.flatspec.AnyFlatSpec
//import org.scalatest.matchers.should.Matchers.{a, convertToAnyShouldWrapper, equal}
//import org.scalatestplus.mockito.MockitoSugar
//import sttp.client.HttpError
//import sttp.model.StatusCode
//import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
//import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}
//
//import java.util.UUID
//import scala.concurrent.{ExecutionContext, Future}
//
//class CustomMetadataServiceSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
//  implicit val ec: ExecutionContext = ExecutionContext.global
//
//  private val graphQLConfig = mock[GraphQLConfiguration]
//  private val customMetadataClient = mock[GraphQLClient[cm.Data, cm.Variables]]
//  private val addBulkMetadataClient = mock[GraphQLClient[abfm.Data, abfm.Variables]]
//  private val deleteFileMetadataClient = mock[GraphQLClient[dfm.Data, dfm.Variables]]
//  private val token = new BearerAccessToken("some-token")
//  private val consignmentId = UUID.fromString("e1ca3948-ee41-4e80-85e6-2123040c135d")
//  private val fileIds: List[UUID] = List(UUID.randomUUID())
//  when(graphQLConfig.getClient[cm.Data, cm.Variables]()).thenReturn(customMetadataClient)
//  when(graphQLConfig.getClient[abfm.Data, abfm.Variables]()).thenReturn(addBulkMetadataClient)
//  when(graphQLConfig.getClient[dfm.Data, dfm.Variables]()).thenReturn(deleteFileMetadataClient)
//
//  private val customMetadataService = new CustomMetadataService(graphQLConfig)
//
//  override def afterEach(): Unit = {
//    Mockito.reset(customMetadataClient)
//    Mockito.reset(addBulkMetadataClient)
//  }
//
//  "customMetadata" should "return the all closure metadata" in {
//    val data: Option[cm.Data] = Some(
//      cm.Data(
//        List(
//          cm.CustomMetadata(
//            "TestProperty",
//            Some("It's the Test Property"),
//            Some("Test Property"),
//            Defined,
//            Some("Test Property Group"),
//            Text,
//            editable = false,
//            multiValue = false,
//            Some("TestValue"),
//            1,
//            List(
//              cm.CustomMetadata.Values(
//                "TestValue",
//                List(cm.CustomMetadata.Values.Dependencies("TestDependency")),
//                1
//              )
//            ),
//            None,
//            allowExport = false
//          ),
//          cm.CustomMetadata(
//            "TestDependency",
//            Some("It's the Test Dependency"),
//            Some("Test Dependency"),
//            Defined,
//            Some("Test Dependency Group"),
//            Text,
//            editable = false,
//            multiValue = false,
//            Some("TestDependencyValue"),
//            2,
//            List(),
//            None,
//            allowExport = false
//          )
//        )
//      )
//    )
//    val response = GraphQlResponse(data, Nil)
//    when(customMetadataClient.getResult(token, cm.document, Some(cm.Variables(consignmentId))))
//      .thenReturn(Future.successful(response))
//
//    val status: List[cm.CustomMetadata] = customMetadataService.getCustomMetadata(consignmentId, token).futureValue
//    status should equal(
//      List(
//        cm.CustomMetadata(
//          "TestProperty",
//          Some("It's the Test Property"),
//          Some("Test Property"),
//          Defined,
//          Some("Test Property Group"),
//          Text,
//          editable = false,
//          multiValue = false,
//          Some("TestValue"),
//          1,
//          List(
//            cm.CustomMetadata.Values(
//              "TestValue",
//              List(cm.CustomMetadata.Values.Dependencies("TestDependency")),
//              1
//            )
//          ),
//          None,
//          allowExport = false
//        ),
//        cm.CustomMetadata(
//          "TestDependency",
//          Some("It's the Test Dependency"),
//          Some("Test Dependency"),
//          Defined,
//          Some("Test Dependency Group"),
//          Text,
//          editable = false,
//          multiValue = false,
//          Some("TestDependencyValue"),
//          2,
//          List(),
//          None,
//          allowExport = false
//        )
//      )
//    )
//  }
//
//  "customMetadata" should "return an error if the API call fails" in {
//    when(customMetadataClient.getResult(token, cm.document, Some(cm.Variables(consignmentId))))
//      .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))
//
//    customMetadataService.getCustomMetadata(consignmentId, token).failed.futureValue shouldBe a[HttpError]
//  }
//
//  "customMetadata" should "throw an AuthorisationException if the API returns an auth error" in {
//    val response = GraphQlResponse[cm.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
//    when(customMetadataClient.getResult(token, cm.document, Some(cm.Variables(consignmentId))))
//      .thenReturn(Future.successful(response))
//
//    val results = customMetadataService.getCustomMetadata(consignmentId, token).failed.futureValue.asInstanceOf[AuthorisationException]
//
//    results shouldBe a[AuthorisationException]
//  }
//
//  "saveMetadata" should "save the metadata" in {
//    val updateFileMetadataInput = List(UpdateFileMetadataInput(filePropertyIsMultiValue = false, "test1", "test2"))
//    val updateBulkFileMetadataInput = UpdateBulkFileMetadataInput(consignmentId, fileIds, updateFileMetadataInput)
//    val variables = Some(abfm.Variables(updateBulkFileMetadataInput))
//    when(addBulkMetadataClient.getResult(token, abfm.document, variables))
//      .thenReturn(Future(GraphQlResponse(Option(abfm.Data(abfm.UpdateBulkFileMetadata(Nil, Nil))), Nil)))
//
//    customMetadataService.saveMetadata(consignmentId, fileIds, token, updateFileMetadataInput).futureValue
//
//    verify(addBulkMetadataClient, times(1)).getResult(token, abfm.document, variables)
//  }
//
//  "saveMetadata" should "return an error if the API call fails" in {
//    val variables = abfm.Variables(UpdateBulkFileMetadataInput(consignmentId, fileIds, Nil))
//    when(addBulkMetadataClient.getResult(token, abfm.document, Option(variables)))
//      .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))
//
//    customMetadataService.saveMetadata(consignmentId, fileIds, token, Nil).failed.futureValue shouldBe a[HttpError]
//  }
//
//  "saveMetadata" should "throw an AuthorisationException if the API returns an auth error" in {
//    val variables = abfm.Variables(UpdateBulkFileMetadataInput(consignmentId, fileIds, Nil))
//    val response = GraphQlResponse[abfm.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
//    when(addBulkMetadataClient.getResult(token, abfm.document, Option(variables)))
//      .thenReturn(Future.successful(response))
//
//    customMetadataService.saveMetadata(consignmentId, fileIds, token, Nil).failed.futureValue shouldBe a[AuthorisationException]
//  }
//
//  "deleteMetadata" should "delete the additional metadata" in {
//    val deleteFileMetadataInput = DeleteFileMetadataInput(fileIds, Some(List("PropertyName1")))
//    val variables = Some(dfm.Variables(deleteFileMetadataInput))
//    val deleteFileMetadata = dfm.DeleteFileMetadata(fileIds, List("PropertyName1"))
//    when(deleteFileMetadataClient.getResult(token, dfm.document, variables))
//      .thenReturn(Future(GraphQlResponse(Option(dfm.Data(deleteFileMetadata)), Nil)))
//
//    val response = customMetadataService.deleteMetadata(fileIds, token, Set("PropertyName1")).futureValue
//
//    response.deleteFileMetadata should equal(deleteFileMetadata)
//    verify(deleteFileMetadataClient, times(1)).getResult(token, dfm.document, variables)
//  }
//
//  "deleteMetadata" should "return an error if the API call fails" in {
//    val variables = dfm.Variables(DeleteFileMetadataInput(fileIds, Some(List("PropertyName1"))))
//    when(deleteFileMetadataClient.getResult(token, dfm.document, Option(variables)))
//      .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))
//
//    customMetadataService.deleteMetadata(fileIds, token, Set("PropertyName1")).failed.futureValue shouldBe a[HttpError]
//  }
//
//  "deleteMetadata" should "throw an AuthorisationException if the API returns an auth error" in {
//    val variables = dfm.Variables(DeleteFileMetadataInput(fileIds, Some(List("PropertyName1"))))
//    val response = GraphQlResponse[dfm.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
//    when(deleteFileMetadataClient.getResult(token, dfm.document, Option(variables)))
//      .thenReturn(Future.successful(response))
//
//    customMetadataService.deleteMetadata(fileIds, token, Set("PropertyName1")).failed.futureValue shouldBe a[AuthorisationException]
//  }
//}
