package services

import cats.effect.IO
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.AddFilesAndMetadata.{addFilesAndMetadata => afam}
import graphql.codegen.StartUpload.{startUpload => su}
import graphql.codegen.types.{AddFileAndMetadataInput, StartUploadInput}
import services.ApiErrorHandling.sendApiRequest
import software.amazon.awssdk.core.internal.async.ByteBuffersAsyncRequestBody
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{PutObjectRequest, PutObjectResponse}
import uk.gov.nationalarchives.aws.utils.s3.S3Utils
import uk.gov.nationalarchives.aws.utils.s3.S3Clients._

import java.nio.file.Files
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters.CompletionStageOps

class UploadService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val startUploadClient = graphqlConfiguration.getClient[su.Data, su.Variables]()
  private val addFilesAndMetadataClient = graphqlConfiguration.getClient[afam.Data, afam.Variables]()

  def startUpload(startUploadInput: StartUploadInput, token: BearerAccessToken): Future[String] = {
    val variables = su.Variables(startUploadInput)
    sendApiRequest(startUploadClient, su.document, token, variables).map(data => data.startUpload)
  }

  def saveClientMetadata(addFileAndMetadataInput: AddFileAndMetadataInput, token: BearerAccessToken): Future[List[afam.AddFilesAndMetadata]] = {
    val variables = afam.Variables(addFileAndMetadataInput)
    sendApiRequest(addFilesAndMetadataClient, afam.document, token, variables).map(data => data.addFilesAndMetadata)
  }

  def uploadDraftMetadata(bucket: String, key: String, draftMetadata: String): Future[PutObjectResponse] = {
    uploadDraftMetadata(bucket, key, draftMetadata, S3Utils(s3Async("https://s3.eu-west-2.amazonaws.com/")))
  }

  def uploadDraftMetadata(bucket: String, key: String, draftMetadata: String, s3client: S3Utils): Future[PutObjectResponse] = {
    val bytes = draftMetadata.getBytes
    val asyncClient = s3Async("https://s3.eu-west-2.amazonaws.com/")

    val publisher = ByteBuffersAsyncRequestBody.from("application/octet-stream", bytes)
    asyncClient.putObject(PutObjectRequest.builder.bucket(bucket).key(key).build, publisher).asScala
  }
}
