package services

import java.util.UUID

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.{Assertion, Matchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import scala.concurrent.{ExecutionContext, Future}

class ConsignmentExportServiceSpec extends WordSpec with Matchers with MockitoSugar {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "triggerExport" should {
    "return the correct value when the api is available" in {
      checkTriggerExport(200, expectedValue = true)
    }
    "return the correct value when the api is not available" in {
      checkTriggerExport(500, expectedValue = false)
    }
  }

  private def checkTriggerExport(responseCode: Int, expectedValue: Boolean): Assertion = {
    val wsClient = mock[WSClient]
    val request= mock[WSRequest]
    val config = mock[Configuration]
    val response = mock[WSResponse]
    when(wsClient.url(any[String])).thenReturn(request)
    when(request.addHttpHeaders(any[(String, String)])).thenReturn(request)
    when(response.status).thenReturn(responseCode)
    when(request.post[String]("{}")).thenReturn(Future(response))

    val service = new ConsignmentExportService(wsClient, config)
    val exportTriggered = service.triggerExport(UUID.randomUUID(), "token").futureValue
    exportTriggered should be(expectedValue)
  }
}
