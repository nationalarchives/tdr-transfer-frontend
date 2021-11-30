package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import services.ApiErrorHandling.sendApiRequest

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import graphql.codegen.Metadata.metadata._


import java.util.UUID

class MetadataService @Inject()(val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val metadataFieldsClient = graphqlConfiguration.getClient[Data, Variables]()

  def getConsignmentMetadataFields(token: BearerAccessToken): Future[List[Metadata]] = {
    sendApiRequest(metadataFieldsClient, document, token, Variables()).map(_.metadata)
  }
}

object MetadataService {

}
