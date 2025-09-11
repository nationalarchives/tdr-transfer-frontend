package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import controllers.util.ConsignmentProperty.{JUDGMENT_REFERENCE, NCN, NO_NCN, NeutralCitationData, tdrDataLoadHeaderMapper}
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

  // Convenience method to add or update the neutral citation number, no neutral citation flag, and judgment reference metadataw
  // will not validated the data, just send it to the API
  def addOrUpdateConsignmentNeutralCitationNumber(
      consignmentId: UUID,
      neutralCitationData: NeutralCitationData,
      token: BearerAccessToken
  ): Future[List[aoucm.AddOrUpdateConsignmentMetadata]] = {
    val metadataList = List(
      ConsignmentMetadata(tdrDataLoadHeaderMapper(NCN), neutralCitationData.neutralCitation.getOrElse("")),
      ConsignmentMetadata(tdrDataLoadHeaderMapper(NO_NCN), neutralCitationData.noNeutralCitation.toString),
      ConsignmentMetadata(tdrDataLoadHeaderMapper(JUDGMENT_REFERENCE), neutralCitationData.judgmentReference.getOrElse(""))
    )
    addOrUpdateConsignmentMetadata(consignmentId, metadataList, token)
  }
}
