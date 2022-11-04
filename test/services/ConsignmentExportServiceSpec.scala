package services

import java.util.UUID
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.UpdateTransferInitiated.updateTransferInitiated._
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import sangria.ast.Document
import sttp.client3.SttpBackend
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class ConsignmentExportServiceSpec extends AnyWordSpec with MockitoSugar {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "triggerExport" should {
    "return the correct value when the api is available" in {
      triggerExport(200, UUID.randomUUID()).futureValue should be(true)
    }
    "return the correct value when the api is not available" in {
      val consignmentId = UUID.randomUUID()
      val message = s"Call to export API has returned a non 200 response for consignment $consignmentId"
      triggerExport(500, consignmentId).failed.futureValue.getMessage should equal(message)
    }
  }

  "updateTransferInitiated" should {
    "send the correct values to the api" in {
      val graphQLConfiguration = mock[GraphQLConfiguration]
      val wsClient = mock[WSClient]
      val config = mock[Configuration]
      val client = mock[GraphQLClient[Data, Variables]]
      val tokenCaptor: ArgumentCaptor[BearerAccessToken] = ArgumentCaptor.forClass(classOf[BearerAccessToken])
      val variablesCaptor: ArgumentCaptor[Option[Variables]] = ArgumentCaptor.forClass(classOf[Option[Variables]])
      when(client.getResult[Future](tokenCaptor.capture(), any[Document], variablesCaptor.capture())(any[SttpBackend[Future, Any]], any[ClassTag[Future[_]]]))
        .thenReturn(Future(GraphQlResponse(Option(Data(Option(1))), List())))
      when(graphQLConfiguration.getClient[Data, Variables]()).thenReturn(client)
      val service = new ConsignmentExportService(wsClient, config, graphQLConfiguration)
      val consignmentId = UUID.randomUUID()
      val token = new BearerAccessToken("token")
      service.updateTransferInitiated(consignmentId, token)
      tokenCaptor.getValue.getValue should equal("token")
      variablesCaptor.getValue.get.consignmentId should equal(consignmentId)
    }

    "return the correct value when the graphql api is available" in {
      updateTransferInitiated(Future(GraphQlResponse(Option(Data(Option(1))), List()))).futureValue should be(true)
    }

    "return the correct value when the graphql api is unavailable" in {
      updateTransferInitiated(Future.failed(new RuntimeException("graphql error"))).failed.futureValue.getMessage should equal("graphql error")
    }
  }

  private def updateTransferInitiated(getResultResponse: Future[GraphQlResponse[Data]]): Future[Boolean] = {
    val graphQLConfiguration = mock[GraphQLConfiguration]
    val wsClient = mock[WSClient]
    val config = mock[Configuration]
    val client = mock[GraphQLClient[Data, Variables]]
    when(client.getResult[Future](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Future, Any]], any[ClassTag[Future[_]]]))
      .thenReturn(getResultResponse)
    when(graphQLConfiguration.getClient[Data, Variables]()).thenReturn(client)
    val service = new ConsignmentExportService(wsClient, config, graphQLConfiguration)
    service.updateTransferInitiated(UUID.randomUUID(), new BearerAccessToken())
  }

  private def triggerExport(responseCode: Int, consignmentId: UUID): Future[Boolean] = {
    val graphQLConfiguration = mock[GraphQLConfiguration]
    val wsClient = mock[WSClient]
    val request = mock[WSRequest]
    val config = mock[Configuration]
    val response = mock[WSResponse]
    when(wsClient.url(any[String])).thenReturn(request)
    when(request.addHttpHeaders(any[(String, String)])).thenReturn(request)
    when(response.status).thenReturn(responseCode)
    when(request.post[String]("{}")).thenReturn(Future(response))

    val service = new ConsignmentExportService(wsClient, config, graphQLConfiguration)
    service.triggerExport(consignmentId, "token")
  }
}
