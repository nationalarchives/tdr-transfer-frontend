package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{ApplicationConfig, GraphQLConfiguration}
import graphql.codegen.AddFilesAndMetadata.{addFilesAndMetadata => afam}
import graphql.codegen.StartUpload.{startUpload => su}
import graphql.codegen.types.{AddFileAndMetadataInput, StartUploadInput}
import services.ApiErrorHandling.sendApiRequest
import software.amazon.awssdk.core.internal.async.ByteBuffersAsyncRequestBody
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{PutObjectRequest, PutObjectResponse}
import uk.gov.nationalarchives.aws.utils.s3.S3Clients._

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters.CompletionStageOps

class UploadService @Inject() (val graphqlConfiguration: GraphQLConfiguration, val applicationConfig: ApplicationConfig)(implicit val ec: ExecutionContext) {
  private val startUploadClient = graphqlConfiguration.getClient[su.Data, su.Variables]()
  private val addFilesAndMetadataClient = graphqlConfiguration.getClient[afam.Data, afam.Variables]()
  private val s3Endpoint = applicationConfig.s3Endpoint

  def startUpload(startUploadInput: StartUploadInput, token: BearerAccessToken): Future[String] = {
    val variables = su.Variables(startUploadInput)
    sendApiRequest(startUploadClient, su.document, token, variables).map(data => data.startUpload)
  }

  def saveClientMetadata(addFileAndMetadataInput: AddFileAndMetadataInput, token: BearerAccessToken): Future[List[afam.AddFilesAndMetadata]] = {
    val variables = afam.Variables(addFileAndMetadataInput)
    sendApiRequest(addFilesAndMetadataClient, afam.document, token, variables).map(data => data.addFilesAndMetadata)
  }

  def uploadDraftMetadata(bucket: String, key: String, draftMetadata: String): Future[PutObjectResponse] = {
    uploadDraftMetadata(bucket, key, draftMetadata, s3Async(s3Endpoint))
  }

  def uploadDraftMetadata(bucket: String, key: String, draftMetadata: String, s3AsyncClient: S3AsyncClient): Future[PutObjectResponse] = {
    val bytes = draftMetadata.getBytes
    val publisher: ByteBuffersAsyncRequestBody = ByteBuffersAsyncRequestBody.from("application/octet-stream", bytes)
    s3AsyncClient.putObject(PutObjectRequest.builder.bucket(bucket).key(key).build, publisher).asScala
  }
}
