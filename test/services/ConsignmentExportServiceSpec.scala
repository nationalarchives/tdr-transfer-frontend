package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.UpdateTransferInitiated.updateTransferInitiated._
import io.circe.Encoder
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.{ConfigLoader, Configuration}
import sangria.ast.Document
import sttp.client3.SttpBackend
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class ConsignmentExportServiceSpec extends AnyWordSpec with MockitoSugar {
  implicit val ec: ExecutionContext = ExecutionContext.global

  "triggerExport" should {
    "trigger the step function with the correct arguments" in {
      val consignmentId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val graphQLConfiguration = mock[GraphQLConfiguration]
      val mockToken = mock[Token]
      val stepFunction = mock[StepFunction]
      val config = mock[Configuration]
      val arnCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val inputCaptor: ArgumentCaptor[ExportStepFunctionInput] = ArgumentCaptor.forClass(classOf[ExportStepFunctionInput])
      val nameCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val execIdCaptor: ArgumentCaptor[UUID] = ArgumentCaptor.forClass(classOf[UUID])
      val encoderCaptor: ArgumentCaptor[Encoder[ExportStepFunctionInput]] = ArgumentCaptor.forClass(classOf[Encoder[ExportStepFunctionInput]])
      when(mockToken.userId).thenReturn(userId)
      when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("stepFunctionArn")
      when(stepFunction.triggerStepFunction(any[String], any[ExportStepFunctionInput], any[String], any[UUID])(any[Encoder[ExportStepFunctionInput]]))
        .thenReturn(Future(true))

      val service = new ConsignmentExportService(stepFunction, config, graphQLConfiguration)
      service.triggerExport(consignmentId, mockToken)
      verify(stepFunction, times(1)).triggerStepFunction(arnCaptor.capture(), inputCaptor.capture(), nameCaptor.capture(), execIdCaptor.capture())(encoderCaptor.capture())
      arnCaptor.getValue shouldBe "stepFunctionArn"
      inputCaptor.getValue.consignmentId shouldBe consignmentId.toString
      nameCaptor.getValue shouldBe "Export"
      execIdCaptor.getValue shouldBe consignmentId
    }
    "return an error if the step function fails to trigger" in {
      val stepFunction = mock[StepFunction]
      val config = mock[Configuration]
      val consignmentId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val mockToken = mock[Token]
      val graphQLConfiguration = mock[GraphQLConfiguration]

      when(mockToken.userId).thenReturn(userId)
      when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("stepFunctionArn")
      when(stepFunction.triggerStepFunction(any[String], any[ExportStepFunctionInput], any[String], any[UUID])(any[Encoder[ExportStepFunctionInput]]))
        .thenThrow(new RuntimeException("something went wrong"))

      val service = new ConsignmentExportService(stepFunction, config, graphQLConfiguration)

      val error = intercept[RuntimeException] {
        service.triggerExport(consignmentId, mockToken)
      }

      error.getMessage should equal("something went wrong")
    }
  }

  "updateTransferInitiated" should {
    "send the correct values to the api" in {
      val graphQLConfiguration = mock[GraphQLConfiguration]
      val stepFunction = mock[StepFunction]
      val config = mock[Configuration]
      val client = mock[GraphQLClient[Data, Variables]]
      val tokenCaptor: ArgumentCaptor[BearerAccessToken] = ArgumentCaptor.forClass(classOf[BearerAccessToken])
      val variablesCaptor: ArgumentCaptor[Option[Variables]] = ArgumentCaptor.forClass(classOf[Option[Variables]])
      when(client.getResult[Future](tokenCaptor.capture(), any[Document], variablesCaptor.capture())(any[SttpBackend[Future, Any]], any[ClassTag[Future[_]]]))
        .thenReturn(Future(GraphQlResponse(Option(Data(Option(1))), List())))
      when(graphQLConfiguration.getClient[Data, Variables]()).thenReturn(client)
      val service = new ConsignmentExportService(stepFunction, config, graphQLConfiguration)
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
    val stepFunction = mock[StepFunction]
    val config = mock[Configuration]
    val client = mock[GraphQLClient[Data, Variables]]
    when(client.getResult[Future](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Future, Any]], any[ClassTag[Future[_]]]))
      .thenReturn(getResultResponse)
    when(graphQLConfiguration.getClient[Data, Variables]()).thenReturn(client)
    val service = new ConsignmentExportService(stepFunction, config, graphQLConfiguration)
    service.updateTransferInitiated(UUID.randomUUID(), new BearerAccessToken())
  }
}
