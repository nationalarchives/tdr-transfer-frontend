package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.{ApplicationConfig, GraphQLConfiguration}
import graphql.codegen.AddFilesAndMetadata.{addFilesAndMetadata => afam}
import graphql.codegen.StartUpload.{startUpload => su}
import graphql.codegen.types.{AddFileAndMetadataInput, ClientSideMetadataInput, StartUploadInput}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{doAnswer, when}
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Configuration
import software.amazon.awssdk.core.internal.async.ByteBuffersAsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{PutObjectRequest, PutObjectResponse}
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Future}

class UploadServiceSpec extends AnyFlatSpec {
  implicit val ec: ExecutionContext = ExecutionContext.global
  private val graphQlConfig = mock[GraphQLConfiguration]
  private val token = new BearerAccessToken("some-token")
  private val configuration: Configuration = mock[Configuration]
  val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)

  "startUpload" should "return the correct data" in {
    val input = StartUploadInput(UUID.randomUUID(), "parent", Some(false))
    val graphQlClientForStartUpload = mock[GraphQLClient[su.Data, su.Variables]]
    when(graphQlConfig.getClient[su.Data, su.Variables]())
      .thenReturn(graphQlClientForStartUpload)

    val graphQlResponse =
      GraphQlResponse(Some(su.Data("ok")), Nil)
    when(graphQlClientForStartUpload.getResult(token, su.document, Some(su.Variables(input))))
      .thenReturn(Future.successful(graphQlResponse))

    val response = new UploadService(graphQlConfig, applicationConfig).startUpload(input, token).futureValue
    response should equal("ok")
  }

  "saveMetadata" should "return the correct data" in {
    val graphQlClientForAddFilesAndMetadata = mock[GraphQLClient[afam.Data, afam.Variables]]
    when(graphQlConfig.getClient[afam.Data, afam.Variables]())
      .thenReturn(graphQlClientForAddFilesAndMetadata)

    val clientSideMetadataInput = ClientSideMetadataInput("originalPath", "checksum", 1, 1, 1) :: Nil
    val addFileAndMetadataInput: AddFileAndMetadataInput = AddFileAndMetadataInput(UUID.randomUUID(), clientSideMetadataInput, Some(Nil))
    val input = afam.AddFilesAndMetadata(UUID.randomUUID(), 0)

    val graphQlResponse =
      GraphQlResponse(Some(afam.Data(List(input))), Nil)
    when(graphQlClientForAddFilesAndMetadata.getResult(token, afam.document, Some(afam.Variables(addFileAndMetadataInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val response = new UploadService(graphQlConfig, applicationConfig).saveClientMetadata(addFileAndMetadataInput, token).futureValue
    response.size should equal(1)
    response.head should equal(input)
  }

  "uploadDraftMetadata" should "return call s3AsyncClient putObject with correct arguments " in {

    val s3AsyncClient = mock[S3AsyncClient]
    val putObjectResponse = PutObjectResponse.builder().eTag("testEtag").build()

    val putObjectRequestCaptor: ArgumentCaptor[PutObjectRequest] = ArgumentCaptor.forClass(classOf[PutObjectRequest])
    val requestBodyCaptor: ArgumentCaptor[ByteBuffersAsyncRequestBody] = ArgumentCaptor.forClass(classOf[ByteBuffersAsyncRequestBody])
    val mockResponse = CompletableFuture.completedFuture(putObjectResponse)
    doAnswer(_ => mockResponse).when(s3AsyncClient).putObject(putObjectRequestCaptor.capture(), requestBodyCaptor.capture())
    when(configuration.get[String]("s3.endpoint")).thenReturn("http://localhost:9009")

    new UploadService(graphQlConfig, applicationConfig)
      .uploadDraftMetadata("test-draft-metadata-bucket", "draft-metadata.csv", "id,code\n12,A", s3AsyncClient)

    putObjectRequestCaptor.getValue.bucket() shouldBe "test-draft-metadata-bucket"
    putObjectRequestCaptor.getValue.key() shouldBe "draft-metadata.csv"
    requestBodyCaptor.getValue.contentLength().get() shouldBe 12
  }

  "uploadDraftMetadata" should "return error when unable to save draft metadata" in {
    val s3AsyncClient = mock[S3AsyncClient]
    val mockResponse = CompletableFuture.failedFuture(new RuntimeException("Failed to upload"))
    doAnswer(_ => mockResponse).when(s3AsyncClient).putObject(any[PutObjectRequest], any[ByteBuffersAsyncRequestBody])
    when(configuration.get[String]("s3.endpoint")).thenReturn("http://localhost:9009")

    val response = new UploadService(graphQlConfig, applicationConfig)
      .uploadDraftMetadata("test-draft-metadata-bucket", "draft-metadata.csv", "id,code\n12,A", s3AsyncClient)
      .failed
      .futureValue
    response.getMessage shouldBe "Failed to upload"
  }
}
