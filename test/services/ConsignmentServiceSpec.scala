package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetConsignment.{getConsignment => gc}
import graphql.codegen.AddConsignment.addConsignment
import graphql.codegen.types.AddConsignmentInput
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{HttpError, NothingT, SttpBackend}
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContext, Future}

class ConsignmentServiceSpec extends WordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend()

  private val graphQlConfig = mock[GraphQLConfiguration]
  private val getConsignmentClient = mock[GraphQLClient[gc.Data, gc.Variables]]
  private val addConsignmentClient = mock[GraphQLClient[addConsignment.Data, addConsignment.Variables]]
  when(graphQlConfig.getClient[gc.Data, gc.Variables]()).thenReturn(getConsignmentClient)
  when(graphQlConfig.getClient[addConsignment.Data, addConsignment.Variables]()).thenReturn(addConsignmentClient)

  private val consignmentService = new ConsignmentService(graphQlConfig)

  private val token = new BearerAccessToken("some-token")
  private val consignmentId = UUID.fromString("180f9166-fe3c-486e-b9ab-6dfa5f3058dc")
  private val seriesId = UUID.fromString("d54a5118-33a0-4ba2-8030-d16efcf1d1f4")

  override def afterEach(): Unit = {
    Mockito.reset(getConsignmentClient)
  }

  "consignmentExists" should {
    "Return true when given a valid consignment id" in {
      val response = GraphQlResponse(Some(gc.Data(Some(gc.GetConsignment(consignmentId, seriesId)))), Nil)
      when(getConsignmentClient.getResult(token, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignment = consignmentService.consignmentExists(consignmentId, token)
      val actualResults = getConsignment.futureValue

      actualResults should be(true)
    }

    "Return false if consignment with given id does not exist" in {
      val response = GraphQlResponse(Some(gc.Data(None)), Nil)
      when(getConsignmentClient.getResult(token, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignment = consignmentService.consignmentExists(consignmentId, token)
      val actualResults = getConsignment.futureValue

      actualResults should be(false)
    }

    "Return an error when the API has an error" in {
      when(getConsignmentClient.getResult(token, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val results = consignmentService.consignmentExists(consignmentId, token)
      results.failed.futureValue shouldBe a[HttpError]
    }

    "throw an AuthorisationException if the API returns an auth error" in {
      val response = GraphQlResponse[gc.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
      when(getConsignmentClient.getResult(token, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignment = consignmentService.consignmentExists(consignmentId, token)
      val results = getConsignment.failed.futureValue
      results shouldBe a[AuthorisationException]
    }
  }

  "createConsignment" should {
    "create a consignment with the given series" in {
      val response = GraphQlResponse(Some(new addConsignment.Data(addConsignment.AddConsignment(Some(consignmentId), seriesId))), Nil)
      val expectedVariables = Some(addConsignment.Variables(AddConsignmentInput(seriesId)))
      when(addConsignmentClient.getResult(token, addConsignment.document, expectedVariables))
        .thenReturn(Future.successful(response))

      consignmentService.createConsignment(seriesId, token)

      Mockito.verify(addConsignmentClient).getResult(token, addConsignment.document, expectedVariables)
    }


    "return the created consignment" in {
      val response = GraphQlResponse(Some(new addConsignment.Data(addConsignment.AddConsignment(Some(consignmentId), seriesId))), Nil)
      when(addConsignmentClient.getResult(token, addConsignment.document, Some(addConsignment.Variables(AddConsignmentInput(seriesId)))))
        .thenReturn(Future.successful(response))

      val result = consignmentService.createConsignment(seriesId, token).futureValue

      result.consignmentid should contain(consignmentId)
    }

    "Return an error when the API has an error" in {
      when(addConsignmentClient.getResult(token, addConsignment.document, Some(addConsignment.Variables(AddConsignmentInput(seriesId)))))
        .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

      val results = consignmentService.createConsignment(seriesId, token)

      results.failed.futureValue shouldBe a[HttpError]
    }

    "throw an AuthorisationException if the API returns an auth error" in {
      val response = GraphQlResponse[addConsignment.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
      when(addConsignmentClient.getResult(token, addConsignment.document, Some(addConsignment.Variables(AddConsignmentInput(seriesId)))))
        .thenReturn(Future.successful(response))

      val results = consignmentService.createConsignment(seriesId, token).failed.futureValue
      results shouldBe a[AuthorisationException]
    }
  }
}