package services

import configuration.ApplicationConfig
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import services.MessagingService.TransferCompleteEvent
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.PublishResponse
import uk.gov.nationalarchives.aws.utils.sns.SNSClients.sns
import uk.gov.nationalarchives.aws.utils.sns.SNSUtils

import javax.inject.Inject
import scala.concurrent.ExecutionContext

class MessagingService @Inject() (val applicationConfig: ApplicationConfig)(implicit val ec: ExecutionContext) {
  val client: SnsClient = sns(applicationConfig.snsEndpoint)
  val utils: SNSUtils = SNSUtils(client)

  implicit val transferCompletedEventEncoder: Encoder[TransferCompleteEvent] = deriveEncoder[TransferCompleteEvent]
  def sendTransferCompleteNotification(transferCompletedEvent: TransferCompleteEvent): PublishResponse = {
    utils.publish(transferCompletedEvent.asJson.toString, applicationConfig.notificationSnsTopicArn)
  }
}

object MessagingService {
  case class TransferCompleteEvent(
      transferringBodyName: Option[String],
      consignmentReference: String,
      consignmentId: String,
      seriesName: Option[String],
      userId: String,
      userEmail: String
  )
}
