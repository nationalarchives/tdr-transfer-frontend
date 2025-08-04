package services

import cats.implicits.catsSyntaxOptionId
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
      "intg",
      "Mock".some,
      "TDR-2024",
      "c140d49c-93d0-4345-8d71-c97ff28b947e",
      "someSeries".some,
      "c140d49c-93d0-4345-8d71-c97ff28b947e",
      "test@example.com",
      true,
      10
    )
    val expectedMessageString = """{
                                  |  "environment" : "intg",
                                  |  "transferringBodyName" : "Mock",
                                  |  "consignmentReference" : "TDR-2024",
                                  |  "consignmentId" : "c140d49c-93d0-4345-8d71-c97ff28b947e",
                                  |  "seriesCode" : "someSeries",
                                  |  "userId" : "c140d49c-93d0-4345-8d71-c97ff28b947e",
                                  |  "userEmail" : "test@example.com",
                                  |  "closedRecords" : true,
                                  |  "totalRecords" : 10
                                  |}""".stripMargin
    service.sendMetadataReviewRequestNotification(metadataReviewRequestEvent)
    verify(mockUtils).publish(expectedMessageString, testArn)
  }

  "sendMetadataReviewSubmittedNotification" should "call publish with the correct parameters" in {
    val service = createService
    val metadataReviewSubmittedEvent = MessagingService.MetadataReviewSubmittedEvent(
      environment = "intg",
      consignmentReference = "Ref123",
      urlLink = "example.com",
      userEmail = "user@example.com",
      status = "Status",
      transferringBodyName = "ABCD".some,
      seriesCode = "1234".some,
      userId = "some",
      closedRecords = true,
      totalRecords = 10
    )
    val expectedMessageString = """{
                                  |  "environment" : "intg",
                                  |  "consignmentReference" : "Ref123",
                                  |  "urlLink" : "example.com",
                                  |  "userEmail" : "user@example.com",
                                  |  "status" : "Status",
                                  |  "transferringBodyName" : "ABCD",
                                  |  "seriesCode" : "1234",
                                  |  "userId" : "some",
                                  |  "closedRecords" : true,
                                  |  "totalRecords" : 10
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
