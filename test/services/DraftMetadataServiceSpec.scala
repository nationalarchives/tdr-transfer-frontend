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
import play.api.{ConfigLoader, Configuration}
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class DraftMetadataServiceSpec extends AnyWordSpec with MockitoSugar {

  val uploadFileName = "draft-metadata.csv"

  "triggerDraftMetadataValidator" should {
    "trigger the step function with the correct arguments" in {
      val consignmentId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val stepFunction = mock[StepFunction]
      val config = mock[Configuration]
      val applicationConfig = mock[ApplicationConfig]
      val downloadService = mock[DownloadService]
      val token = mock[Token]
      val arnCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val inputCaptor: ArgumentCaptor[DraftMetadataStepFunctionInput] = ArgumentCaptor.forClass(classOf[DraftMetadataStepFunctionInput])
      val nameCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val execIdCaptor: ArgumentCaptor[UUID] = ArgumentCaptor.forClass(classOf[UUID])
      val encoderCaptor: ArgumentCaptor[Encoder[DraftMetadataStepFunctionInput]] = ArgumentCaptor.forClass(classOf[Encoder[DraftMetadataStepFunctionInput]])
      when(token.userId).thenReturn(userId)
      when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("stepFunctionArn")
      when(stepFunction.triggerStepFunction(any[String], any[DraftMetadataStepFunctionInput], any[String], any[UUID])(any[Encoder[DraftMetadataStepFunctionInput]]))
        .thenReturn(Future(true))
      val service = new DraftMetadataService(stepFunction, config, applicationConfig, downloadService)

      service.triggerDraftMetadataValidator(consignmentId, uploadFileName, token).futureValue
      verify(stepFunction, times(1)).triggerStepFunction(arnCaptor.capture(), inputCaptor.capture(), nameCaptor.capture(), execIdCaptor.capture())(encoderCaptor.capture())
      arnCaptor.getValue shouldBe "stepFunctionArn"
      inputCaptor.getValue.consignmentId shouldBe consignmentId.toString
      inputCaptor.getValue.fileName shouldBe uploadFileName
      nameCaptor.getValue shouldBe "Metadata Validation"
      execIdCaptor.getValue shouldBe consignmentId
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
      when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("stepFunctionArn")
      when(stepFunction.triggerStepFunction(any[String], any[BackendChecksStepFunctionInput], any[String], any[UUID])(any[Encoder[BackendChecksStepFunctionInput]]))
        .thenThrow(new RuntimeException("something went wrong"))

      val service = new DraftMetadataService(stepFunction, config, applicationConfig, downloadService)

      val error = intercept[RuntimeException] {
        service.triggerDraftMetadataValidator(consignmentId, uploadFileName, token)
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
      val service = new DraftMetadataService(stepFunction, config, applicationConfig, downloadService)

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
      val service = new DraftMetadataService(stepFunction, config, applicationConfig, downloadService)

      Await.result(service.getErrorTypeFromErrorJson(UUID.randomUUID()), Duration("1 seconds")) shouldBe FileError.UNKNOWN
    }

  }
}
