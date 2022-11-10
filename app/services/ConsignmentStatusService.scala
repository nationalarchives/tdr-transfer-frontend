package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.{GetConsignment, Variables}
import services.ApiErrorHandling._

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentStatusService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val getConsignmentStatusClient = graphqlConfiguration.getClient[gcs.Data, gcs.Variables]()

  def getConsignmentStatus(consignmentId: UUID, token: BearerAccessToken): Future[Option[GetConsignment.CurrentStatus]] = {
    val variables = new Variables(consignmentId)
    sendApiRequest(getConsignmentStatusClient, gcs.document, token, variables).map(data => data.getConsignment.map(_.currentStatus))
  }

  def consignmentStatusSeries(consignmentId: UUID, token: BearerAccessToken): Future[Option[GetConsignment]] = {
    val variables = new Variables(consignmentId)
    sendApiRequest(getConsignmentStatusClient, gcs.document, token, variables).map(data => data.getConsignment)
  }
}
