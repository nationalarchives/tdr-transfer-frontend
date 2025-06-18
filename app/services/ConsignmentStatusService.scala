package services

import services.DynamoService.ConsignmentStatuses
import services.Statuses.{FailedValue, StatusType, StatusValue}
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentStatusService @Inject() (val dynamoService: DynamoService)(implicit val ec: ExecutionContext) {

  def getStatusValues(statuses: List[ConsignmentStatuses], statusTypes: StatusType*): Map[StatusType, Option[String]] = {
    statusTypes
      .map(t => {
        val value = statuses.find(_.statusType == t.id).map(_.value)
        t -> value
      })
      .toMap
  }

  def getConsignmentStatuses(consignmentId: UUID): Future[List[ConsignmentStatuses]] = {
    dynamoService.getConsignment(consignmentId).map(_.consignmentStatuses)
  }

  def addConsignmentStatus(consignmentId: UUID, statusType: String, statusValue: String): Future[UpdateItemResponse] = {
    dynamoService.setFieldToValue(consignmentId, s"status_$statusType", statusValue)
  }

  def updateConsignmentStatus(consignmentId: UUID, statusType: String, statusValue: String): Future[Int] = {
    dynamoService.setFieldToValue(consignmentId, s"status_$statusType", statusValue).map(_.sdkHttpResponse().statusCode())
  }

  def consignmentStatusSeries(consignmentId: UUID): Future[Option[ConsignmentStatuses]] = {
    getConsignmentStatuses(consignmentId).map(_.find(_.statusType == "Series"))
  }

  def getSeries(consignmentId: UUID): Future[Option[String]] = dynamoService.getConsignment(consignmentId).map(_.seriesName)

}

object ConsignmentStatusService {
  def statusValue(statusType: StatusType): Seq[ConsignmentStatuses] => StatusValue =
    _.find(_.statusType == statusType.id).map(cs => StatusValue(cs.value)).getOrElse(FailedValue)
}
