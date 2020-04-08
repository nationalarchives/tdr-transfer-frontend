package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetConsignment.{getConsignment => gc}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.{BeforeAndAfterEach, Matchers, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import sttp.client.HttpError
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContext, Future}

class ConsignmentServiceSpec extends WordSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQlConfig = mock[GraphQLConfiguration]
  private val graphQlClient = mock[GraphQLClient[gc.Data, gc.Variables]]
  when(graphQlConfig.getClient[gc.Data, gc.Variables]()).thenReturn(graphQlClient)

  private val consignmentService = new ConsignmentService(graphQlConfig)

  private val token = new BearerAccessToken("some-token")
  private val consignmentId = UUID.fromString("180f9166-fe3c-486e-b9ab-6dfa5f3058dc")
  private val seriesId = UUID.fromString("d54a5118-33a0-4ba2-8030-d16efcf1d1f4")

  override def afterEach(): Unit = {
    Mockito.reset(graphQlClient)
  }

  "consignmentExists" should {
    "Return true when given a valid consignment id" in {
      val response = GraphQlResponse(Some(gc.Data(Some(gc.GetConsignment(consignmentId, seriesId)))), Nil)
      when(graphQlClient.getResult(token, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignment = consignmentService.consignmentExists(consignmentId, token)
      val actualResults = getConsignment.futureValue

      actualResults should be(true)
    }

    "Return false if consignment with given id does not exist" in {
      val response = GraphQlResponse(Some(gc.Data(None)), Nil)
      when(graphQlClient.getResult(token, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignment = consignmentService.consignmentExists(consignmentId, token)
      val actualResults = getConsignment.futureValue

      actualResults should be(false)
    }

    "Return an error when the API has an error" in {
      when(graphQlClient.getResult(token, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.failed(HttpError("something went wrong")))

      val results = consignmentService.consignmentExists(consignmentId, token)
      results.failed.futureValue shouldBe a[HttpError]
    }

    "throw an AuthorisationException if the API returns an auth error" in {
      val response = GraphQlResponse[gc.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
      when(graphQlClient.getResult(token, gc.document, Some(gc.Variables(consignmentId))))
        .thenReturn(Future.successful(response))

      val getConsignment = consignmentService.consignmentExists(consignmentId, token)
      val results = getConsignment.failed.futureValue
      results shouldBe a[AuthorisationException]
    }
  }
}