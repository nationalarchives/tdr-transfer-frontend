package services

import cats.effect.IO
import cats.effect.IO.raiseError
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.AddFilesAndMetadata.{addFilesAndMetadata => afam}
import graphql.codegen.StartUpload.{startUpload => su}
import graphql.codegen.types.{AddFileAndMetadataInput, StartUploadInput}
import services.ApiErrorHandling.sendApiRequest
import software.amazon.awssdk.core.internal.async.ByteBuffersAsyncRequestBody
import software.amazon.awssdk.transfer.s3.model.CompletedUpload
import uk.gov.nationalarchives.DAS3Client

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

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

   def uploadDraftMetadata(bucket:String, key:String, draftMetadata: String): IO[Any] = {
        val s3: DAS3Client[IO] = DAS3Client[IO]()
        val bytes = draftMetadata.getBytes
        val publisher = ByteBuffersAsyncRequestBody.from("application/octet-stream", bytes)
        s3.upload(bucket, key, bytes.size, publisher)
   }
}
