package services

import configuration.ApplicationConfig
import software.amazon.awssdk.core.internal.async.ByteBuffersAsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{PutObjectRequest, PutObjectResponse}
import uk.gov.nationalarchives.aws.utils.s3.S3Clients._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters.CompletionStageOps

class UploadService @Inject() (val applicationConfig: ApplicationConfig)(implicit val ec: ExecutionContext) {
  private val s3Endpoint = applicationConfig.s3Endpoint

  def uploadDraftMetadata(bucket: String, key: String, bytes: Array[Byte]): Future[PutObjectResponse] = {
    uploadDraftMetadata(bucket, key, bytes, s3Async(s3Endpoint))
  }

  def uploadDraftMetadata(bucket: String, key: String, bytes: Array[Byte], s3AsyncClient: S3AsyncClient): Future[PutObjectResponse] = {
    val publisher: ByteBuffersAsyncRequestBody = ByteBuffersAsyncRequestBody.from("application/octet-stream", bytes)
    s3AsyncClient.putObject(PutObjectRequest.builder.bucket(bucket).key(key).build, publisher).asScala
  }
}
