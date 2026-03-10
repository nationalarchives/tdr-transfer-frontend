package services

import io.circe.Encoder
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.{ConfigLoader, Configuration}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BackendChecksServiceSpec extends AnyWordSpec with MockitoSugar {
  "triggerBackendChecks" should {
    "trigger the step function with the correct arguments" in {
      val consignmentId = UUID.randomUUID()
      val stepFunction = mock[StepFunction]
      val config = mock[Configuration]
      val arnCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val inputCaptor: ArgumentCaptor[BackendChecksStepFunctionInput] = ArgumentCaptor.forClass(classOf[BackendChecksStepFunctionInput])
      val nameCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val execIdCaptor: ArgumentCaptor[UUID] = ArgumentCaptor.forClass(classOf[UUID])
      val encoderCaptor: ArgumentCaptor[Encoder[BackendChecksStepFunctionInput]] = ArgumentCaptor.forClass(classOf[Encoder[BackendChecksStepFunctionInput]])

      when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("stepFunctionArn")
      when(stepFunction.triggerStepFunction(any[String], any[BackendChecksStepFunctionInput], any[String], any[UUID])(any[Encoder[BackendChecksStepFunctionInput]]))
        .thenReturn(Future(true))

      val service = new BackendChecksService(config, stepFunction)
      service.triggerBackendChecks(consignmentId)
      verify(stepFunction, times(1)).triggerStepFunction(arnCaptor.capture(), inputCaptor.capture(), nameCaptor.capture(), execIdCaptor.capture())(encoderCaptor.capture())
      arnCaptor.getValue shouldBe "stepFunctionArn"
      inputCaptor.getValue.consignmentId shouldBe consignmentId.toString
      nameCaptor.getValue shouldBe "Backend Checks"
      execIdCaptor.getValue shouldBe consignmentId
    }

    "return an error if the step function fails to trigger" in {
      val stepFunction = mock[StepFunction]
      val config = mock[Configuration]
      val consignmentId = UUID.randomUUID()

      when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("stepFunctionArn")
      when(stepFunction.triggerStepFunction(any[String], any[BackendChecksStepFunctionInput], any[String], any[UUID])(any[Encoder[BackendChecksStepFunctionInput]]))
        .thenThrow(new RuntimeException("something went wrong"))

      val service = new BackendChecksService(config, stepFunction)

      val error = intercept[RuntimeException] {
        service.triggerBackendChecks(consignmentId)
      }

      error.getMessage should equal("something went wrong")
    }
  }
}
