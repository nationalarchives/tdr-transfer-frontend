package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.AddOrUpdateConsignmenetMetadata.{addOrUpdateConsignmentMetadata => aoucm}
import graphql.codegen.types.{AddOrUpdateConsignmentMetadataInput, ConsignmentMetadata}
import services.ApiErrorHandling._

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentMetadataService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val addOrUpdateConsignmentMetadataClient = graphqlConfiguration.getClient[aoucm.Data, aoucm.Variables]()

  /** Call GraphQL mutation to add or update consignment level metadata entries.
    * @param consignmentId
    *   The consignment identifier
    * @param consignmentMetadata
    *   List of metadata key/value pairs (as generated ConsignmentMetadata objects)
    * @param token
    *   Auth token
    */
  def addOrUpdateConsignmentMetadata(
      consignmentId: UUID,
      consignmentMetadata: List[ConsignmentMetadata],
      token: BearerAccessToken
  ): Future[List[aoucm.AddOrUpdateConsignmentMetadata]] = {
    val input = AddOrUpdateConsignmentMetadataInput(consignmentId, consignmentMetadata)
    val variables = aoucm.Variables(input)
    sendApiRequest(addOrUpdateConsignmentMetadataClient, aoucm.document, token, variables).map(_.addOrUpdateConsignmentMetadata)
  }
}
