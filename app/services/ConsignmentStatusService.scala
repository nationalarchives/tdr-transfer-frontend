package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.{GetConsignment, Variables}
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import graphql.codegen.AddConsignmentStatus.addConsignmentStatus.{Variables => acsv}
import graphql.codegen.AddConsignmentStatus.{addConsignmentStatus => acs}
import graphql.codegen.types.ConsignmentStatusInput
import services.ApiErrorHandling._
import services.Statuses.StatusType

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentStatusService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val getConsignmentStatusClient = graphqlConfiguration.getClient[gcs.Data, gcs.Variables]()
  private val addConsignmentStatusClient = graphqlConfiguration.getClient[acs.Data, acs.Variables]()

  def getStatusValues(statuses: List[ConsignmentStatuses], statusTypes: StatusType*): Map[StatusType, Option[String]] = {
    statusTypes
      .map(t => {
        val value = statuses.find(_.statusType == t.id).map(_.value)
        t -> value
      })
      .toMap
  }

  def getConsignmentStatuses(consignmentId: UUID, token: BearerAccessToken): Future[List[ConsignmentStatuses]] = {
    val variables = new Variables(consignmentId)
    sendApiRequest(getConsignmentStatusClient, gcs.document, token, variables).map(data => data.getConsignment.map(_.consignmentStatuses)).map {
      case Some(value) => value
      case _           => Nil
    }
  }

  def addConsignmentStatus(consignmentId: UUID, statusType: String, statusValue: String, token: BearerAccessToken): Future[acs.Data] = {
    val variables = new acsv(ConsignmentStatusInput(consignmentId, statusType, Some(statusValue)))
    sendApiRequest(addConsignmentStatusClient, acs.document, token, variables)
  }

  def consignmentStatusSeries(consignmentId: UUID, token: BearerAccessToken): Future[Option[GetConsignment]] = {
    val variables = new Variables(consignmentId)
    sendApiRequest(getConsignmentStatusClient, gcs.document, token, variables).map(data => data.getConsignment)
  }

}
