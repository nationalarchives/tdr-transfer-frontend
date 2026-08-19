package services

import configuration.ApplicationConfig
import io.circe.Encoder
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import uk.gov.nationalarchives.tdr.common.utils.serviceinputs.Inputs.{BackendChecksInput, MetadataValidationInput}
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class DraftMetadataServiceSpec extends AnyWordSpec with MockitoSugar {

  val uploadFileName = "draft-metadata.csv"
  val consignmentRef: String = "TEST-TDR-2021-GB"

  "triggerDraftMetadataValidator" should {
    "trigger the step function with the correct arguments" in {
      val consignmentId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val stepFunction = mock[StepFunction]
      val applicationConfig = mock[ApplicationConfig]
      val downloadService = mock[DownloadService]
      val token = mock[Token]
      val arnCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val inputCaptor: ArgumentCaptor[MetadataValidationInput] = ArgumentCaptor.forClass(classOf[MetadataValidationInput])
      val nameCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val execIdCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val encoderCaptor: ArgumentCaptor[Encoder[MetadataValidationInput]] = ArgumentCaptor.forClass(classOf[Encoder[MetadataValidationInput]])
      when(token.userId).thenReturn(userId)
      when(applicationConfig.metadataValidationStepFunctionArn).thenReturn("stepFunctionArn")
      when(stepFunction.triggerStepFunction(any[String], any[MetadataValidationInput], any[String], any[String])(any[Encoder[MetadataValidationInput]]))
        .thenReturn(Future(true))
      val service = new DraftMetadataService(stepFunction, applicationConfig, downloadService)

      service.triggerDraftMetadataValidator(consignmentId, consignmentRef, uploadFileName, token).futureValue
      verify(stepFunction, times(1)).triggerStepFunction(arnCaptor.capture(), inputCaptor.capture(), nameCaptor.capture(), execIdCaptor.capture())(encoderCaptor.capture())
      arnCaptor.getValue shouldBe "stepFunctionArn"
      inputCaptor.getValue.consignmentId shouldBe consignmentId.toString
      inputCaptor.getValue.fileName shouldBe uploadFileName
      nameCaptor.getValue shouldBe "Metadata Validation"
      execIdCaptor.getValue.contains(consignmentRef) shouldBe true
    }

    "return an error if the step function fails to trigger" in {
      val stepFunction = mock[StepFunction]
      val config = mock[Configuration]
      val applicationConfig = mock[ApplicationConfig]
      val downloadService = mock[DownloadService]
      val token = mock[Token]
      val consignmentId = UUID.randomUUID()
      val userId = UUID.randomUUID()

      when(token.userId).thenReturn(userId)
      when(applicationConfig.metadataValidationStepFunctionArn).thenReturn("stepFunctionArn")
      when(stepFunction.triggerStepFunction(any[String], any[BackendChecksInput], any[String], any[String])(any[Encoder[BackendChecksInput]]))
        .thenThrow(new RuntimeException("something went wrong"))

      val service = new DraftMetadataService(stepFunction, applicationConfig, downloadService)

      val error = intercept[RuntimeException] {
        service.triggerDraftMetadataValidator(consignmentId, consignmentRef, uploadFileName, token)
      }

      error.getMessage should equal("something went wrong")
    }
  }
  "getErrorType" should {
    val stepFunction = mock[StepFunction]
    val config = mock[Configuration]
    val downloadService = mock[DownloadService]

    "get error type from error json file" in {
      val errorJson =
        """
          |{
          |  "consignmentId" : "f82af3bf-b742-454c-9771-bfd6c5eae749",
          |  "date" : "$today",
          |  "fileError" : "NONE",
          |  "validationErrors" : [
          |  ]
          |}
          |""".stripMargin
      val mockResponse = GetObjectResponse.builder().build()
      val p: ResponseBytes[GetObjectResponse] = ResponseBytes.fromByteArray(mockResponse, errorJson.getBytes())
      when(config.get[String]("draftMetadata.errorFileName")).thenReturn("error.json")
      when(config.get[String]("draft_metadata_s3_bucket_name")).thenReturn("bucket")
      val applicationConfig: ApplicationConfig = new ApplicationConfig(config)
      when(downloadService.downloadFile(anyString, anyString)).thenReturn(Future.successful(p))
      val service = new DraftMetadataService(stepFunction, applicationConfig, downloadService)

      Await.result(service.getErrorTypeFromErrorJson(UUID.randomUUID()), Duration("1 seconds")) shouldBe FileError.NONE
    }

    "get error type will be unspecified if none in json" in {
      val errorJson =
        """
          |{
          |  "consignmentId" : "f82af3bf-b742-454c-9771-bfd6c5eae749",
          |  "date" : "$today",
          |   |  "validationErrors" : [
          |  ]
          |}
          |""".stripMargin
      val mockResponse = GetObjectResponse.builder().build()
      val p: ResponseBytes[GetObjectResponse] = ResponseBytes.fromByteArray(mockResponse, errorJson.getBytes())
      when(config.get[String]("draftMetadata.errorFileName")).thenReturn("error.json")
      when(config.get[String]("draft_metadata_s3_bucket_name")).thenReturn("bucket")
      val applicationConfig: ApplicationConfig = new ApplicationConfig(config)
      when(downloadService.downloadFile(anyString, anyString)).thenReturn(Future.successful(p))
      val service = new DraftMetadataService(stepFunction, applicationConfig, downloadService)

      Await.result(service.getErrorTypeFromErrorJson(UUID.randomUUID()), Duration("1 seconds")) shouldBe FileError.UNKNOWN
    }

  }
}
