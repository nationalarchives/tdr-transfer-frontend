package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.{GetConsignment, Variables}
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import graphql.codegen.AddConsignmentStatus.{addConsignmentStatus => acs}
import graphql.codegen.UpdateConsignmentStatus.{updateConsignmentStatus => ucs}
import graphql.codegen.types.ConsignmentStatusInput
import services.ApiErrorHandling._
import services.Statuses.{NotEnteredValue, StatusType, StatusValue}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentStatusService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val getConsignmentStatusClient = graphqlConfiguration.getClient[gcs.Data, gcs.Variables]()
  private val addConsignmentStatusClient = graphqlConfiguration.getClient[acs.Data, acs.Variables]()
  private val updateConsignmentStatusClient = graphqlConfiguration.getClient[ucs.Data, ucs.Variables]()

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

  def addConsignmentStatus(consignmentId: UUID, statusType: String, statusValue: String, token: BearerAccessToken): Future[acs.AddConsignmentStatus] = {
    val variables = new acs.Variables(ConsignmentStatusInput(consignmentId, statusType, Some(statusValue)))
    sendApiRequest(addConsignmentStatusClient, acs.document, token, variables).map(_.addConsignmentStatus)
  }

  def updateConsignmentStatus(consignmentStatusInput: ConsignmentStatusInput, token: BearerAccessToken): Future[Int] = {
    val variables = ucs.Variables(consignmentStatusInput)
    sendApiRequest(updateConsignmentStatusClient, ucs.document, token, variables).map(data => {
      data.updateConsignmentStatus match {
        case Some(response) => response
        case None           => throw new RuntimeException(s"No data returned when updating the consignment status for ${consignmentStatusInput.consignmentId}")
      }
    })
  }

  def consignmentStatusSeries(consignmentId: UUID, token: BearerAccessToken): Future[Option[GetConsignment]] = {
    val variables = new Variables(consignmentId)
    sendApiRequest(getConsignmentStatusClient, gcs.document, token, variables).map(data => data.getConsignment)
  }

}

object ConsignmentStatusService {
  def statusValue(statusType: StatusType): Seq[ConsignmentStatuses] => StatusValue =
    _.find(_.statusType == statusType.id).map(cs => StatusValue(cs.value)).getOrElse(NotEnteredValue)
}
