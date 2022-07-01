package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetClosureMetadata.closureMetadata.{ClosureMetadata, Variables}
import graphql.codegen.GetClosureMetadata.{closureMetadata => cm}
import services.ApiErrorHandling._
import uk.gov.nationalarchives.tdr.GraphQLClient

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class CustomMetadataService @Inject()(val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val closureMetadataStatusClient: GraphQLClient[cm.Data, Variables] = graphqlConfiguration.getClient[cm.Data, cm.Variables]()

  def getClosureMetadata(consignmentId: UUID, token: BearerAccessToken): Future[List[ClosureMetadata]] = {
    val variables = new Variables(consignmentId)
    sendApiRequest(closureMetadataStatusClient, cm.document, token, variables).map(data => data.closureMetadata)
  }
}
