package services

import configuration.ApplicationConfig
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ValidateMetadataServiceSpec extends AnyWordSpec with MockitoSugar {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val configuration: Configuration = mock[Configuration]

  "triggerMetadataValidation" should {
    "return the correct value when the api is available" in {
      triggerValidateMetadata(200, UUID.randomUUID()).futureValue should be(true)
    }
    
    "return the correct value when the api is not available" in {
      val consignmentId = UUID.randomUUID()
      val message = s"Call to validate draft metadata API has returned a non 200 response for consignment $consignmentId"
      triggerValidateMetadata(500, consignmentId).failed.futureValue.getMessage should equal(message)
    }
  }

  private def triggerValidateMetadata(responseCode: Int, consignmentId: UUID): Future[Boolean] = {
    when(configuration.get[String]("metadatavalidation.baseUrl")).thenReturn("http://localhost:9009")
    val applicationConfig: ApplicationConfig = new ApplicationConfig(configuration)
    val wsClient = mock[WSClient]
    val request = mock[WSRequest]
    val response = mock[WSResponse]
    when(wsClient.url(any[String])).thenReturn(request)
    when(request.addHttpHeaders(any[(String, String)])).thenReturn(request)
    when(response.status).thenReturn(responseCode)
    when(request.post[String]("{}")).thenReturn(Future(response))

    val service = new ValidateMetadataService(wsClient, applicationConfig)
    service.triggerMetadataValidation(consignmentId, "token")
  }
}
