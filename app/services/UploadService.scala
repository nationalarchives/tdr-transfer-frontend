package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.StartUpload.{startUpload => su}
import graphql.codegen.AddFilesAndMetadata.{addFilesAndMetadata => afam}
import graphql.codegen.UpdateConsignmentStatus.{updateConsignmentStatus => ucs}
import graphql.codegen.types.{AddFileAndMetadataInput, ConsignmentStatusInput, StartUploadInput}
import services.ApiErrorHandling.sendApiRequest

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UploadService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val startUploadClient = graphqlConfiguration.getClient[su.Data, su.Variables]()
  private val addFilesAndMetadataClient = graphqlConfiguration.getClient[afam.Data, afam.Variables]()
  private val updateConsignmentStatusClient = graphqlConfiguration.getClient[ucs.Data, ucs.Variables]()

//  def updateConsignmentStatus(consignmentStatusInput: ConsignmentStatusInput, token: BearerAccessToken): Future[Int] = {
//    val variables = ucs.Variables(consignmentStatusInput)
//    sendApiRequest(updateConsignmentStatusClient, ucs.document, token, variables).map(data => {
//      data.updateConsignmentStatus match {
//        case Some(response) => response
//        case None           => throw new RuntimeException(s"No data returned when updating the consignment status for ${consignmentStatusInput.consignmentId}")
//      }
//    })
//  }

  def startUpload(startUploadInput: StartUploadInput, token: BearerAccessToken): Future[String] = {
    val variables = su.Variables(startUploadInput)
    sendApiRequest(startUploadClient, su.document, token, variables).map(data => data.startUpload)
  }

  def saveClientMetadata(addFileAndMetadataInput: AddFileAndMetadataInput, token: BearerAccessToken): Future[List[afam.AddFilesAndMetadata]] = {
    val variables = afam.Variables(addFileAndMetadataInput)
    sendApiRequest(addFilesAndMetadataClient, afam.document, token, variables).map(data => data.addFilesAndMetadata)
  }
}
