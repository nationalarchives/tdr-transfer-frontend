package services

import configuration.ApplicationConfig
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax.EncoderOps
import services.MessagingService.{MetadataReviewSubmittedEvent, MetadataReviewRequestEvent, TransferCompleteEvent}
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
  implicit val metadataReviewRequestEventEncoder: Encoder[MetadataReviewRequestEvent] = deriveEncoder[MetadataReviewRequestEvent]
  implicit val metadataReviewSubmittedEventEncoder: Encoder[MetadataReviewSubmittedEvent] = deriveEncoder[MetadataReviewSubmittedEvent]

  def sendTransferCompleteNotification(transferCompletedEvent: TransferCompleteEvent): PublishResponse = {
    utils.publish(transferCompletedEvent.asJson.toString, applicationConfig.notificationSnsTopicArn)
  }

  def sendMetadataReviewRequestNotification(metadataReviewRequestEvent: MetadataReviewRequestEvent): PublishResponse = {
    utils.publish(metadataReviewRequestEvent.asJson.toString, applicationConfig.notificationSnsTopicArn)
  }

  def sendMetadataReviewSubmittedNotification(metadataReviewSubmittedEvent: MetadataReviewSubmittedEvent): PublishResponse = {
    utils.publish(metadataReviewSubmittedEvent.asJson.toString, applicationConfig.notificationSnsTopicArn)
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
  case class MetadataReviewRequestEvent(
      transferringBodyName: Option[String],
      consignmentReference: String,
      consignmentId: String,
      userId: String,
      userEmail: String
  )
  case class MetadataReviewSubmittedEvent(
      consignmentReference: String,
      urlLink: String
  )
}
