package services

import configuration.ApplicationConfig
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import uk.gov.nationalarchives.aws.utils.sns.SNSUtils
import org.mockito.Mockito.{verify, when}
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.Configuration
import software.amazon.awssdk.services.sns.SnsClient

import scala.concurrent.ExecutionContext

class MessagingServiceSpec extends AnyFlatSpec with Matchers {

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global
  val mockSnsClient: SnsClient = mock[SnsClient]
  val mockUtils: SNSUtils = mock[SNSUtils]
  val config: Configuration = mock[Configuration]
  val testArn = "arn:test-arn"

  "sendTransferCompleteNotification" should "call publish with the correct parameters" in {
    val service = createService
    val transferCompleteEvent = MessagingService.TransferCompleteEvent(
      transferringBodyName = Some("TransferringBodyName"),
      consignmentReference = "Ref123",
      consignmentId = "ConsID456",
      seriesName = Some("SeriesXYZ"),
      userId = "UserID789",
      userEmail = "user@example.com"
    )
    val expectedMessageString = """{
                                  |  "transferringBodyName" : "TransferringBodyName",
                                  |  "consignmentReference" : "Ref123",
                                  |  "consignmentId" : "ConsID456",
                                  |  "seriesName" : "SeriesXYZ",
                                  |  "userId" : "UserID789",
                                  |  "userEmail" : "user@example.com"
                                  |}""".stripMargin
    service.sendTransferCompleteNotification(transferCompleteEvent)
    verify(mockUtils).publish(expectedMessageString, testArn)
  }

  "sendMetadataReviewRequestNotification" should "call publish with the correct parameters" in {
    val service = createService
    val metadataReviewRequestEvent = MessagingService.MetadataReviewRequestEvent(
      transferringBodyName = Some("TransferringBodyName"),
      consignmentReference = "Ref123",
      consignmentId = "ConsID456",
      seriesName = Some("SomeSeries"),
      userId = "UserID789",
      userEmail = "user@example.com"
    )
    val expectedMessageString = """{
                                  |  "transferringBodyName" : "TransferringBodyName",
                                  |  "consignmentReference" : "Ref123",
                                  |  "consignmentId" : "ConsID456",
                                  |  "seriesName" : "SomeSeries",
                                  |  "userId" : "UserID789",
                                  |  "userEmail" : "user@example.com"
                                  |}""".stripMargin
    service.sendMetadataReviewRequestNotification(metadataReviewRequestEvent)
    verify(mockUtils).publish(expectedMessageString, testArn)
  }

  "sendMetadataReviewSubmittedNotification" should "call publish with the correct parameters" in {
    val service = createService
    val metadataReviewSubmittedEvent = MessagingService.MetadataReviewSubmittedEvent(
      consignmentReference = "Ref123",
      urlLink = "example.com",
      userEmail = "user@example.com",
      status = "Status"
    )
    val expectedMessageString = """{
                                  |  "consignmentReference" : "Ref123",
                                  |  "urlLink" : "example.com",
                                  |  "userEmail" : "user@example.com",
                                  |  "status" : "Status"
                                  |}""".stripMargin
    service.sendMetadataReviewSubmittedNotification(metadataReviewSubmittedEvent)
    verify(mockUtils).publish(expectedMessageString, testArn)
  }

  def createService: MessagingService = {
    when(config.get[String]("sns.endpoint")).thenReturn("http://localhost:9009")
    when(config.get[String]("notificationSnsTopicArn")).thenReturn(testArn)
    val appConfig = new ApplicationConfig(config)
    new MessagingService(appConfig)(ec) {
      override val client: SnsClient = mockSnsClient
      override val utils: SNSUtils = mockUtils
    }
  }
}
