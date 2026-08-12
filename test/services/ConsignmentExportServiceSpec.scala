package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{ApplicationConfig, GraphQLConfiguration}
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
import uk.gov.nationalarchives.tdr.common.utils.serviceinputs.Inputs.ExportInput
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag

class ConsignmentExportServiceSpec extends AnyWordSpec with MockitoSugar {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val consignmentRef: String = "TEST-TDR-2021-GB"

  "triggerExport" should {
    "trigger the step function with the correct arguments" in {
      val consignmentId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val graphQLConfiguration = mock[GraphQLConfiguration]
      val mockToken = mock[Token]
      val stepFunction = mock[StepFunction]
      val applicationConfig = mock[ApplicationConfig]
      val arnCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val inputCaptor: ArgumentCaptor[ExportInput] = ArgumentCaptor.forClass(classOf[ExportInput])
      val nameCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val execIdCaptor: ArgumentCaptor[String] = ArgumentCaptor.forClass(classOf[String])
      val encoderCaptor: ArgumentCaptor[Encoder[ExportInput]] = ArgumentCaptor.forClass(classOf[Encoder[ExportInput]])
      when(mockToken.userId).thenReturn(userId)
      when(applicationConfig.exportStepFunctionArn).thenReturn("stepFunctionArn")
      when(stepFunction.triggerStepFunction(any[String], any[ExportInput], any[String], any[String])(any[Encoder[ExportInput]]))
        .thenReturn(Future(true))

      when(stepFunction.triggerStepFunction(any[String], any[ExportInput], any[String], any[String])(any[Encoder[ExportInput]]))
        .thenReturn(Future(true))

      val service = new ConsignmentExportService(stepFunction, applicationConfig, graphQLConfiguration)
      service.triggerExport(consignmentId, consignmentRef, mockToken)
      verify(stepFunction, times(1)).triggerStepFunction(arnCaptor.capture(), inputCaptor.capture(), nameCaptor.capture(), execIdCaptor.capture())(encoderCaptor.capture())
      arnCaptor.getValue shouldBe "stepFunctionArn"
      inputCaptor.getValue.consignmentId shouldBe consignmentId.toString
      nameCaptor.getValue shouldBe "Export"
      execIdCaptor.getValue.contains(consignmentRef) shouldBe true
    }

    "return an error if the step function fails to trigger" in {
      val stepFunction = mock[StepFunction]
      val applicationConfig = mock[ApplicationConfig]
      val consignmentId = UUID.randomUUID()
      val userId = UUID.randomUUID()
      val mockToken = mock[Token]
      val graphQLConfiguration = mock[GraphQLConfiguration]

      when(mockToken.userId).thenReturn(userId)
      when(applicationConfig.exportStepFunctionArn).thenReturn("stepFunctionArn")
      when(stepFunction.triggerStepFunction(any[String], any[ExportInput], any[String], any[String])(any[Encoder[ExportInput]]))
        .thenThrow(new RuntimeException("something went wrong"))

      val service = new ConsignmentExportService(stepFunction, applicationConfig, graphQLConfiguration)

      val error = intercept[RuntimeException] {
        service.triggerExport(consignmentId, consignmentRef, mockToken)
      }

      error.getMessage should equal("something went wrong")
    }
  }

  "updateTransferInitiated" should {
    "send the correct values to the api" in {
      val graphQLConfiguration = mock[GraphQLConfiguration]
      val stepFunction = mock[StepFunction]
      val applicationConfig = mock[ApplicationConfig]
      val client = mock[GraphQLClient[Data, Variables]]
      val tokenCaptor: ArgumentCaptor[BearerAccessToken] = ArgumentCaptor.forClass(classOf[BearerAccessToken])
      val variablesCaptor: ArgumentCaptor[Option[Variables]] = ArgumentCaptor.forClass(classOf[Option[Variables]])
      when(client.getResult[Future](tokenCaptor.capture(), any[Document], variablesCaptor.capture())(any[SttpBackend[Future, Any]], any[ClassTag[Future[_]]]))
        .thenReturn(Future(GraphQlResponse(Option(Data(Option(1))), List())))
      when(graphQLConfiguration.getClient[Data, Variables]()).thenReturn(client)
      val service = new ConsignmentExportService(stepFunction, applicationConfig, graphQLConfiguration)
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
    val applicationConfig = mock[ApplicationConfig]
    val client = mock[GraphQLClient[Data, Variables]]
    when(client.getResult[Future](any[BearerAccessToken], any[Document], any[Option[Variables]])(any[SttpBackend[Future, Any]], any[ClassTag[Future[_]]]))
      .thenReturn(getResultResponse)
    when(graphQLConfiguration.getClient[Data, Variables]()).thenReturn(client)
    val service = new ConsignmentExportService(stepFunction, applicationConfig, graphQLConfiguration)
    service.updateTransferInitiated(UUID.randomUUID(), new BearerAccessToken())
  }
}
