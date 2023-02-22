package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.{GetConsignment, Variables}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import services.ApiErrorHandling._
import services.ConsignmentStatusService.StatusType

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentStatusService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val getConsignmentStatusClient = graphqlConfiguration.getClient[gcs.Data, gcs.Variables]()

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

  def consignmentStatusSeries(consignmentId: UUID, token: BearerAccessToken): Future[Option[GetConsignment]] = {
    val variables = new Variables(consignmentId)
    sendApiRequest(getConsignmentStatusClient, gcs.document, token, variables).map(data => data.getConsignment)
  }
}

object ConsignmentStatusService {
  sealed trait StatusType { val id: String }

  case object Series extends StatusType { val id: String = "Series" }
  case object Upload extends StatusType { val id: String = "Upload" }
  case object TransferAgreement extends StatusType { val id: String = "TransferAgreement" }
  case object Export extends StatusType { val id: String = "Export" }
}
