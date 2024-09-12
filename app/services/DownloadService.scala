package services

import configuration.ApplicationConfig
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{GetObjectRequest, GetObjectResponse}
import uk.gov.nationalarchives.aws.utils.s3.S3Clients._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters.CompletionStageOps

class DownloadService @Inject() (val applicationConfig: ApplicationConfig)(implicit val ec: ExecutionContext) {
  private val s3Endpoint = applicationConfig.s3Endpoint

  def downloadFile(bucket: String, key: String): Future[ResponseBytes[GetObjectResponse]] = {
    downloadFile(bucket, key, s3Async(s3Endpoint))
  }

  private def downloadFile(bucket: String, key: String, s3AsyncClient: S3AsyncClient): Future[ResponseBytes[GetObjectResponse]] = {
    val getObjectRequest = GetObjectRequest.builder.bucket(bucket).key(key).build()
    s3AsyncClient.getObject(getObjectRequest, AsyncResponseTransformer.toBytes[GetObjectResponse]).asScala
  }
}
