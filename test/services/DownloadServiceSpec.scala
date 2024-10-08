package services

import configuration.ApplicationConfig
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatestplus.mockito.MockitoSugar.mock
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse}

import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Future}

class DownloadServiceSpec extends AnyFlatSpec {

  implicit val ec: ExecutionContext = ExecutionContext.global

  val mockAppConfig: ApplicationConfig = mock[ApplicationConfig]
  val s3Endpoint = "https://mock-s3-endpoint.com"
  val s3AsyncClient: S3AsyncClient = mock[S3AsyncClient]
  when(mockAppConfig.s3Endpoint).thenReturn(s3Endpoint)

  val downloadService = new DownloadService(mockAppConfig)
  val bucket = "my-test-bucket"
  val key = "test-file.txt"

  "DownloadService" should "download a file using an S3AsyncClient returning the response wrapped in a Scala Future" in {

    val mockResponseBytes = mock[ResponseBytes[GetObjectResponse]]
    val mockCompletableFuture = CompletableFuture.completedFuture(mockResponseBytes)

    when(s3AsyncClient.getObject(any[GetObjectRequest], any[AsyncResponseTransformer[GetObjectResponse, ResponseBytes[GetObjectResponse]]]))
      .thenReturn(mockCompletableFuture)

    val result: Future[ResponseBytes[GetObjectResponse]] = downloadService.downloadFile(bucket, key, s3AsyncClient)

    result.futureValue shouldBe mockResponseBytes

  }

  "DownloadService" should "pass through exceptions when downloading a file from S3" in {
    val mockException = new RuntimeException("S3 error")
    val mockFailedFuture = new CompletableFuture[ResponseBytes[GetObjectResponse]]()

    mockFailedFuture.completeExceptionally(mockException)

    when(s3AsyncClient.getObject(any[GetObjectRequest], any[AsyncResponseTransformer[GetObjectResponse, ResponseBytes[GetObjectResponse]]]))
      .thenReturn(mockFailedFuture)

    val result = downloadService.downloadFile(bucket, key, s3AsyncClient)

    result.failed.futureValue shouldBe mockException

  }
}
