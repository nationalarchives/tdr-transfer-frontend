package services

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
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

  "triggerBackendChecks" should {
    "call the correct url" in {
      val wsClient = mock[WSClient]
      val request = mock[WSRequest]
      val config = mock[Configuration]
      val response = mock[WSResponse]
      val argumentCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("http://localhost")
      when(wsClient.url(argumentCaptor.capture())).thenReturn(request)
      when(request.addHttpHeaders(any[(String, String)])).thenReturn(request)
      when(response.status).thenReturn(200)
      when(request.post[String]("{}")).thenReturn(Future(response))
      val service = new BackendChecksService(wsClient, config)
      val consignmentId = UUID.randomUUID()
      service.triggerBackendChecks(consignmentId, "token").futureValue
      argumentCaptor.getValue should equal(s"http://localhost/backend-checks/$consignmentId")
    }

    "return true if the API response is 200" in {
      val wsClient = mock[WSClient]
      val request = mock[WSRequest]
      val config = mock[Configuration]
      val response = mock[WSResponse]
      when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("http://localhost")
      when(wsClient.url(any[String])).thenReturn(request)
      when(request.addHttpHeaders(any[(String, String)])).thenReturn(request)
      when(response.status).thenReturn(200)
      when(request.post[String]("{}")).thenReturn(Future(response))
      val service = new BackendChecksService(wsClient, config)
      val consignmentId = UUID.randomUUID()
      val triggerResponse = service.triggerBackendChecks(consignmentId, "token").futureValue
      triggerResponse should equal(true)
    }

    "return an error if the API response is 500" in {
      val wsClient = mock[WSClient]
      val request = mock[WSRequest]
      val config = mock[Configuration]
      val response = mock[WSResponse]
      when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("http://localhost")
      when(wsClient.url(any[String])).thenReturn(request)
      when(request.addHttpHeaders(any[(String, String)])).thenReturn(request)
      when(response.status).thenReturn(500)
      when(request.post[String]("{}")).thenReturn(Future(response))
      val service = new BackendChecksService(wsClient, config)
      val consignmentId = UUID.randomUUID()
      val exception = service.triggerBackendChecks(consignmentId, "token").failed.futureValue
      exception.getMessage should equal(s"Call to backend checks API has returned a non 200 response for consignment $consignmentId")
    }
  }
}
