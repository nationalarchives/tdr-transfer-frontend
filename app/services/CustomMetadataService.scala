package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetCustomMetadata.customMetadata.{CustomMetadata, Variables}
import graphql.codegen.GetCustomMetadata.{customMetadata => cm}
import services.ApiErrorHandling._
import uk.gov.nationalarchives.tdr.GraphQLClient

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CustomMetadataService @Inject()(val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val customMetadataStatusClient: GraphQLClient[cm.Data, Variables] = graphqlConfiguration.getClient[cm.Data, cm.Variables]()

  def getCustomMetadata(consignmentId: UUID, token: BearerAccessToken): Future[List[CustomMetadata]] = {
    val variables = new Variables(consignmentId)
    sendApiRequest(customMetadataStatusClient, cm.document, token, variables).map(data => data.customMetadata)
  }
}
