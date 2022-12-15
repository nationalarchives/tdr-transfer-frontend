package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetDisplayProperties.displayProperties.{DisplayProperties, Variables}
import graphql.codegen.GetDisplayProperties.{displayProperties => dp}
import graphql.codegen.types.DataType
import services.ApiErrorHandling._
import uk.gov.nationalarchives.tdr.GraphQLClient

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class DisplayPropertiesService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val displayPropertiesClient: GraphQLClient[dp.Data, Variables] = graphqlConfiguration.getClient[dp.Data, dp.Variables]()

  def getDisplayProperties(consignmentId: UUID, token: BearerAccessToken): Future[List[DisplayProperties]] = {
    val variables = new Variables(consignmentId)
    sendApiRequest(displayPropertiesClient, dp.document, token, variables).map(data => data.displayProperties)
  }
}

case class DisplayProperty(
    active: Boolean,
    componentType: String,
    dataType: DataType,
    description: String,
    displayName: String,
    editable: Boolean,
    group: String,
    label: String,
    multiValue: Boolean,
    ordinal: Int,
    propertyName: String,
    propertyType: String
)
