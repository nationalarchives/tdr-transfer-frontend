package services

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import graphql.codegen.AddFilesAndMetadata.{addFilesAndMetadata => afam}
import graphql.codegen.StartUpload.{startUpload => su}
import graphql.codegen.types.{AddFileAndMetadataInput, ClientSideMetadataInput, StartUploadInput}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.reactivestreams.Publisher
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar.mock
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.transfer.s3.model.CompletedUpload
import uk.gov.nationalarchives.DAS3Client
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.nio.ByteBuffer
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class UploadServiceSpec extends AnyFlatSpec {
  implicit val ec: ExecutionContext = ExecutionContext.global
  private val graphQlConfig = mock[GraphQLConfiguration]
  private val token = new BearerAccessToken("some-token")

  "startUpload" should "return the correct data" in {
    val input = StartUploadInput(UUID.randomUUID(), "parent", Some(false))
    val graphQlClientForStartUpload = mock[GraphQLClient[su.Data, su.Variables]]
    when(graphQlConfig.getClient[su.Data, su.Variables]())
      .thenReturn(graphQlClientForStartUpload)

    val graphQlResponse =
      GraphQlResponse(Some(su.Data("ok")), Nil)
    when(graphQlClientForStartUpload.getResult(token, su.document, Some(su.Variables(input))))
      .thenReturn(Future.successful(graphQlResponse))

    val response = new UploadService(graphQlConfig).startUpload(input, token).futureValue
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

    val response = new UploadService(graphQlConfig).saveClientMetadata(addFileAndMetadataInput, token).futureValue
    response.size should equal(1)
    response.head should equal(input)
  }

  "uploadDraftMetadata" should "return an IO[CompletedUpload] indicating success " in {

    val s3client = mock[DAS3Client[IO]]
    val putObjectResponse = PutObjectResponse.builder().eTag("testEtag").build()
    val completedUpload = IO(CompletedUpload.builder().response(putObjectResponse).build())
    when(s3client.upload(any[String], any[String], any[Long], any[Publisher[ByteBuffer]])).thenReturn(completedUpload)

    val completedUploadIO: IO[CompletedUpload] = new UploadService(graphQlConfig).uploadDraftMetadata("test-draft-metadata-bucket", "draft-metadata.csv", "id,code\n12,A", s3client)

    completedUploadIO.unsafeRunSync().response().eTag() shouldBe "testEtag"
  }

  "uploadDraftMetadata" should "return an IO[CompletedUpload] error when unable to save draft metadata " in {

    val completedUploadIO: IO[CompletedUpload] = new UploadService(graphQlConfig).uploadDraftMetadata("test-draft-metadata-bucket", "draft-metadata.csv", "id,code\n12,A")

    completedUploadIO.recoverWith(error => IO(error.toString)).unsafeRunSync().toString should include("S3Exception")
  }

}
