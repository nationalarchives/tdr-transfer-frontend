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


  "uploadDraftMetadata" should "return call s3AsyncClient putObject with correct arguments " in {

    val s3AsyncClient = mock[S3AsyncClient]
    val putObjectResponse = PutObjectResponse.builder().eTag("testEtag").build()

    val putObjectRequestCaptor: ArgumentCaptor[PutObjectRequest] = ArgumentCaptor.forClass(classOf[PutObjectRequest])
    val requestBodyCaptor: ArgumentCaptor[ByteBuffersAsyncRequestBody] = ArgumentCaptor.forClass(classOf[ByteBuffersAsyncRequestBody])
    val mockResponse = CompletableFuture.completedFuture(putObjectResponse)
    doAnswer(_ => mockResponse).when(s3AsyncClient).putObject(putObjectRequestCaptor.capture(), requestBodyCaptor.capture())
    when(configuration.get[String]("s3.endpoint")).thenReturn("http://localhost:9009")

    new UploadService(applicationConfig)
      .uploadDraftMetadata("test-draft-metadata-bucket", "draft-metadata.csv", "id,code\n12,A".getBytes, s3AsyncClient)

    putObjectRequestCaptor.getValue.bucket() shouldBe "test-draft-metadata-bucket"
    putObjectRequestCaptor.getValue.key() shouldBe "draft-metadata.csv"
    requestBodyCaptor.getValue.contentLength().get() shouldBe 12
  }

  "uploadDraftMetadata" should "return error when unable to save draft metadata" in {
    val s3AsyncClient = mock[S3AsyncClient]
    val mockResponse = CompletableFuture.failedFuture(new RuntimeException("Failed to upload"))
    doAnswer(_ => mockResponse).when(s3AsyncClient).putObject(any[PutObjectRequest], any[ByteBuffersAsyncRequestBody])
    when(configuration.get[String]("s3.endpoint")).thenReturn("http://localhost:9009")

    val response = new UploadService(applicationConfig)
      .uploadDraftMetadata("test-draft-metadata-bucket", "draft-metadata.csv", "id,code\n12,A".getBytes, s3AsyncClient)
      .failed
      .futureValue
    response.getMessage shouldBe "Failed to upload"
  }
}
