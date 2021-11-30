package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentMetadata.getConsignmentMetadata.GetConsignment.Files.AllMetadata
import services.ApiErrorHandling.sendApiRequest

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import graphql.codegen.GetConsignmentMetadata.getConsignmentMetadata._

import java.util.UUID

class FileMetadataService @Inject()(val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val metadataClient = graphqlConfiguration.getClient[Data, Variables]()

  def getConsignmentMetadata(consignmentId: UUID, fileId: UUID, token: BearerAccessToken): Future[List[AllMetadata]] = {
    sendApiRequest(metadataClient, document, token, Variables(consignmentId, Some(fileId))).map(gc => gc.getConsignment match {
      case Some(value) => value.files.flatMap(_.allMetadata)
      case None => Nil
    })
  }

}
