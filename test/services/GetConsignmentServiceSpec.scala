package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignment.getConsignment._
import graphql.codegen.GetConsignment.{getConsignment => gc}
import org.mockito.ArgumentCaptor
import org.mockito.Mockito._
import org.scalatest.Matchers._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.time.{Millis, Seconds, Span}
import sangria.ast.Document
import sttp.client.HttpError
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.GraphqlError
import util.FrontEndTestHelper

import scala.concurrent.{ExecutionContext, Future}

class GetConsignmentServiceSpec extends FrontEndTestHelper {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))
  case class GraphqlData(data: Option[Data], errors: List[GraphqlError] = Nil)

  private def verifyCaptors(captors: (ArgumentCaptor[Document], ArgumentCaptor[BearerAccessToken], ArgumentCaptor[Option[Variables]]), consignmentId: UUID): Unit = {
    val (documentCaptor, tokenCaptor, variablesCaptor) = captors
    documentCaptor.getValue should be(gc.document)
    tokenCaptor.getValue.getValue should be("someAccessToken")
    variablesCaptor.getValue.isDefined should be(true)
    variablesCaptor.getValue.get.consignmentId should be(consignmentId)
  }

  def mockOkResponse(graphQLClient: GraphQLClient[gc.Data, gc.Variables], data: Option[gc.Data], errors: List[GraphqlError]):
  (ArgumentCaptor[Document], ArgumentCaptor[BearerAccessToken], ArgumentCaptor[Option[Variables]])= {
    val documentCaptor: ArgumentCaptor[Document] = ArgumentCaptor.forClass(classOf[Document])
    val tokenCaptor: ArgumentCaptor[BearerAccessToken] = ArgumentCaptor.forClass(classOf[BearerAccessToken])
    val variablesCaptor: ArgumentCaptor[Option[gc.Variables]] = ArgumentCaptor.forClass(classOf[Option[gc.Variables]])

    val graphqlData = graphQLClient.GraphqlData(data, errors)

    when(graphQLClient.getResult(tokenCaptor.capture(), documentCaptor.capture(), variablesCaptor.capture())).thenReturn(Future(graphqlData))
    Tuple3(documentCaptor, tokenCaptor, variablesCaptor)
  }

  def mockFailedResponse(graphQLClient: GraphQLClient[gc.Data, gc.Variables]): (ArgumentCaptor[Document], ArgumentCaptor[BearerAccessToken], ArgumentCaptor[Option[Variables]]) = {
    val documentCaptor: ArgumentCaptor[Document] = ArgumentCaptor.forClass(classOf[Document])
    val tokenCaptor: ArgumentCaptor[BearerAccessToken] = ArgumentCaptor.forClass(classOf[BearerAccessToken])
    val variablesCaptor: ArgumentCaptor[Option[gc.Variables]] = ArgumentCaptor.forClass(classOf[Option[gc.Variables]])

    when(graphQLClient.getResult(tokenCaptor.capture(), documentCaptor.capture(), variablesCaptor.capture())).thenReturn(Future.failed(HttpError("")))
    Tuple3(documentCaptor, tokenCaptor, variablesCaptor)
  }

  "GetConsignmentService GET" should {

    "Return true when given a valid consignment id" in {
      val graphQLClient = mock[GraphQLClient[gc.Data, gc.Variables]]
      val graphQLConfig = mock[GraphQLConfiguration]
      when(graphQLConfig.getClient[gc.Data, gc.Variables]()).thenReturn(graphQLClient)

      val consignmentId = UUID.randomUUID()

      val consignmentResponse: gc.GetConsignment = new gc.GetConsignment(UUID.randomUUID(), UUID.randomUUID())

      val captors = mockOkResponse(graphQLClient, Some(gc.Data(Some(consignmentResponse))), List())

      val getConsignment = new GetConsignmentService(graphQLConfig).consignmentExists(consignmentId, new BearerAccessToken("someAccessToken"))
      val actualResults = getConsignment.futureValue

      actualResults should be(true)
      verifyCaptors(captors, consignmentId)
    }

    "Return false if consignment with given id does not exist" in {
      val consignmentId = UUID.randomUUID()
      val graphQLClient = mock[GraphQLClient[gc.Data, gc.Variables]]

      val graphQLConfig = mock[GraphQLConfiguration]
      when(graphQLConfig.getClient[gc.Data, gc.Variables]()).thenReturn(graphQLClient)

      val captors = mockOkResponse(graphQLClient, Option.empty, List(GraphQLClient.GraphqlError("Error", Nil, Nil)))

      val getConsignment = new GetConsignmentService(graphQLConfig).consignmentExists(consignmentId, new BearerAccessToken("someAccessToken"))
      val actualResults = getConsignment.futureValue
      actualResults shouldBe false
      verifyCaptors(captors, consignmentId)
    }

    "Return an error when the API has an error" in {
      val consignmentId = UUID.randomUUID()
      val graphQLClient = mock[GraphQLClient[gc.Data, gc.Variables]]

      val graphQLConfig = mock[GraphQLConfiguration]
      when(graphQLConfig.getClient[gc.Data, gc.Variables]()).thenReturn(graphQLClient)

      val captors = mockFailedResponse(graphQLClient)

      val getConsignment = new GetConsignmentService(graphQLConfig).consignmentExists(consignmentId, new BearerAccessToken("someAccessToken"))

      val results = getConsignment.failed.futureValue

      results shouldBe a[HttpError]
      verifyCaptors(captors, consignmentId)
    }
  }
}