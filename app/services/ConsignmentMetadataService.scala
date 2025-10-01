package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import controllers.util.ConsignmentProperty.tdrDataLoadHeaderMapper
import graphql.codegen.AddOrUpdateConsignmenetMetadata.{addOrUpdateConsignmentMetadata => aoucm}
import graphql.codegen.types.{AddOrUpdateConsignmentMetadata, AddOrUpdateConsignmentMetadataInput}
import services.ApiErrorHandling._

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

class ConsignmentMetadataService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val addOrUpdateConsignmentMetadataClient = graphqlConfiguration.getClient[aoucm.Data, aoucm.Variables]()

  def addOrUpdateConsignmentMetadata(
      consignmentId: UUID,
      consignmentMetadata: Map[String, String],
      token: BearerAccessToken
  ): Future[List[aoucm.AddOrUpdateConsignmentMetadata]] = {

    val metadataList = consignmentMetadata.map(kv => AddOrUpdateConsignmentMetadata(tdrDataLoadHeaderMapper(kv._1), kv._2)).toList
    val input = AddOrUpdateConsignmentMetadataInput(consignmentId, metadataList)
    val variables = aoucm.Variables(input)
    sendApiRequest(addOrUpdateConsignmentMetadataClient, aoucm.document, token, variables).map(_.addOrUpdateConsignmentMetadata)
  }
}
