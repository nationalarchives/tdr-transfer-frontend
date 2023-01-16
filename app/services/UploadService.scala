package services

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.{AccessToken, GoogleCredentials}
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.StartUpload.{startUpload => su}
import graphql.codegen.AddFilesAndMetadata.{addFilesAndMetadata => afam}
import graphql.codegen.UpdateConsignmentStatus.{updateConsignmentStatus => ucs}
import graphql.codegen.types.{AddFileAndMetadataInput, ConsignmentStatusInput, StartUploadInput}
import org.joda.time.LocalDateTime
import services.ApiErrorHandling.sendApiRequest

import java.time.{Duration, LocalDate, ZoneId}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UploadService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val startUploadClient = graphqlConfiguration.getClient[su.Data, su.Variables]()
  private val addFilesAndMetadataClient = graphqlConfiguration.getClient[afam.Data, afam.Variables]()
  private val updateConsignmentStatusClient = graphqlConfiguration.getClient[ucs.Data, ucs.Variables]()

  def getDriveInformation(accessToken: String): Drive#Files#List = {
    val expiry = LocalDateTime.now().plusHours(2).toDate()
    val credentials = GoogleCredentials.newBuilder.setAccessToken(new AccessToken(accessToken, expiry)).build
    val httpCredentials: HttpCredentialsAdapter = new HttpCredentialsAdapter(credentials)

    val transport = GoogleNetHttpTransport.newTrustedTransport()
    val jsonFactory = GsonFactory.getDefaultInstance
    val service = new Drive.Builder(transport, jsonFactory, httpCredentials).build()
    service.files().list()
  }

  def updateConsignmentStatus(consignmentStatusInput: ConsignmentStatusInput, token: BearerAccessToken): Future[Int] = {
    val variables = ucs.Variables(consignmentStatusInput)
    sendApiRequest(updateConsignmentStatusClient, ucs.document, token, variables).map(data => {
      data.updateConsignmentStatus match {
        case Some(response) => response
        case None           => throw new RuntimeException(s"No data returned when updating the consignment status for ${consignmentStatusInput.consignmentId}")
      }
    })
  }

  def startUpload(startUploadInput: StartUploadInput, token: BearerAccessToken): Future[String] = {
    val variables = su.Variables(startUploadInput)
    sendApiRequest(startUploadClient, su.document, token, variables).map(data => data.startUpload)
  }

  def saveClientMetadata(addFileAndMetadataInput: AddFileAndMetadataInput, token: BearerAccessToken): Future[List[afam.AddFilesAndMetadata]] = {
    val variables = afam.Variables(addFileAndMetadataInput)
    sendApiRequest(addFilesAndMetadataClient, afam.document, token, variables).map(data => data.addFilesAndMetadata)
  }
}
