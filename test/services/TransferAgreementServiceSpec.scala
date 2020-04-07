package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar
import graphql.codegen.IsTransferAgreementComplete.{isTransferAgreementComplete => taComplete}
import graphql.codegen.IsTransferAgreementComplete.isTransferAgreementComplete._
import org.mockito.Mockito
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContext, Future}

class TransferAgreementServiceSpec extends FlatSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQlConfig = mock[GraphQLConfiguration]
  private val graphQlClient = mock[GraphQLClient[taComplete.Data, taComplete.Variables]]

  // TODO: Inject client rather than creating each time
  private var transferAgreementService = new TransferAgreementService(graphQlConfig)

  private val consignmentId = UUID.fromString("da84d99f-469d-4893-8c7b-46900cfa1a8f")
  private val token = new BearerAccessToken("some-token")
  private val variables = Variables(consignmentId)

  override def beforeEach(): Unit = {
    when(graphQlConfig.getClient[taComplete.Data, taComplete.Variables]()).thenReturn(graphQlClient)

    transferAgreementService = new TransferAgreementService(graphQlConfig)
  }

  override def afterEach(): Unit = {
    Mockito.reset(graphQlClient)
  }

  "transferAgreementExists" should "be true if the API check returns 'true'" in {
    val response = GraphQlResponse(Some(Data(Some(GetTransferAgreement(true)))), Nil)

    when(graphQlClient.getResult(token, document, Some(variables)))
      .thenReturn(Future.successful(response))

    transferAgreementService.transferAgreementExists(consignmentId, token).futureValue should be(true)
  }

  "transferAgreementExists" should "be false if the API check returns 'false'" in {
    val response = GraphQlResponse(Some(Data(Some(GetTransferAgreement(false)))), Nil)

    when(graphQlClient.getResult(token, document, Some(variables)))
      .thenReturn(Future.successful(response))

    transferAgreementService.transferAgreementExists(consignmentId, token).futureValue should be(false)
  }


}
