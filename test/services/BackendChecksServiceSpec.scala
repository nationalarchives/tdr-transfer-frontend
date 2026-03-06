package services

import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.{ConfigLoader, Configuration}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BackendChecksServiceSpec extends AnyWordSpec with MockitoSugar {
  implicit val inputEncoder: Encoder[BackendChecksStepFunctionInput] = deriveEncoder[BackendChecksStepFunctionInput]

  "triggerBackendChecks" should {
    "call the correct url" in {
      val stepFunction = mock[StepFunction]
      val config = mock[Configuration]
      val arnCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val inputCaptor: ArgumentCaptor[BackendChecksStepFunctionInput] = ArgumentCaptor.forClass(classOf[BackendChecksStepFunctionInput])
      val nameCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val execId: ArgumentCaptor[UUID] = ArgumentCaptor.forClass(classOf[UUID])
      val consignmentId = UUID.randomUUID()
      when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("stepFunctionArn")
      when(stepFunction.triggerStepFunction("arn", BackendChecksStepFunctionInput(consignmentId.toString), "Some name", consignmentId))
        .thenReturn(Future(true))
//      when(wsClient.url(argumentCaptor.capture())).thenReturn(request)
//      when(request.addHttpHeaders(any[(String, String)])).thenReturn(request)
//      when(response.status).thenReturn(200)
//      when(request.post[String]("{}")).thenReturn(Future(response))
      val service = new BackendChecksService(config, stepFunction)
//      val consignmentId = UUID.randomUUID()
      service.triggerBackendChecks(consignmentId)
      verify(stepFunction, times(1)).triggerStepFunction("stepFunctionArn", BackendChecksStepFunctionInput(consignmentId.toString), "Backend Checks", consignmentId)
//      argumentCaptor.getValue should equal(s"http://localhost/backend-checks/$consignmentId")
    }

    "return true if the API response is 200" in {
      val stepFunction = mock[StepFunction]
      val request = mock[WSRequest]
      val config = mock[Configuration]
      val response = mock[WSResponse]
      when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("http://localhost")
//      when(wsClient.url(any[String])).thenReturn(request)
      when(request.addHttpHeaders(any[(String, String)])).thenReturn(request)
      when(response.status).thenReturn(200)
      when(request.post[String]("{}")).thenReturn(Future(response))
      val service = new BackendChecksService(config, stepFunction)
      val consignmentId = UUID.randomUUID()
      val triggerResponse = service.triggerBackendChecks(consignmentId).futureValue
      triggerResponse should equal(true)
    }

    "return an error if the API response is 500" in {
      val stepFunction = mock[StepFunction]
      val request = mock[WSRequest]
      val config = mock[Configuration]
      val response = mock[WSResponse]
      when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("http://localhost")
//      when(wsClient.url(any[String])).thenReturn(request)
      when(request.addHttpHeaders(any[(String, String)])).thenReturn(request)
      when(response.status).thenReturn(500)
      when(request.post[String]("{}")).thenReturn(Future(response))
      val service = new BackendChecksService(config, stepFunction)
      val consignmentId = UUID.randomUUID()
      val exception = service.triggerBackendChecks(consignmentId).failed.futureValue
      exception.getMessage should equal(s"Call to backend checks API has returned a non 200 response for consignment $consignmentId")
    }
  }
}
