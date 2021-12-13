package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import controllers.{FinalTransferConfirmationData, TransferAgreementData}
import errors.AuthorisationException
import graphql.codegen.AddFinalTransferConfirmation.AddFinalTransferConfirmation.AddFinalTransferConfirmation
import graphql.codegen.AddFinalTransferConfirmation.{AddFinalTransferConfirmation => aftc}
import graphql.codegen.types.AddFinalTransferConfirmationInput
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar
import sttp.client.HttpError
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ConfirmTransferServiceSpec extends FlatSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQlConfig = mock[GraphQLConfiguration]
  private val graphQlClient = mock[GraphQLClient[aftc.Data, aftc.Variables]]
  when(graphQlConfig.getClient[aftc.Data, aftc.Variables]())
    .thenReturn(graphQlClient) // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.

  private val confirmTransferService: ConfirmTransferService = new ConfirmTransferService(graphQlConfig)
  private val consignmentId = UUID.fromString("e1ca3948-ee41-4e80-85e6-2123040c135d")

  private val token = new BearerAccessToken("some-token")

  private val formData = FinalTransferConfirmationData(
    openRecords = true,
    transferLegalOwnership = true)

  val addFinalTransferConfirmationInput: AddFinalTransferConfirmationInput = AddFinalTransferConfirmationInput(
    consignmentId,
    formData.openRecords,
    formData.transferLegalOwnership
  )

  override def afterEach(): Unit = {
    Mockito.reset(graphQlClient)
  }

  "addFinalTransferConfirmation" should "return the TransferConfirmation from the API" in {
    val finalTransferConfirmationResponse = AddFinalTransferConfirmation(
      consignmentId,
      finalOpenRecordsConfirmed = true,
      legalOwnershipTransferConfirmed = true)

    val graphQlResponse =
      GraphQlResponse(Some(aftc.Data(
        finalTransferConfirmationResponse
      )), Nil) // Please ignore the "Type mismatch" error that IntelliJ displays, as it is incorrect.
    when(graphQlClient.getResult(token, aftc.document, Some(aftc.Variables(addFinalTransferConfirmationInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferConfirmation: AddFinalTransferConfirmation = confirmTransferService.addFinalTransferConfirmation(consignmentId, token, formData)
      .futureValue.asInstanceOf[AddFinalTransferConfirmation]

    transferConfirmation.consignmentId should equal(consignmentId)
    transferConfirmation.finalOpenRecordsConfirmed should equal(formData.openRecords)
    transferConfirmation.legalOwnershipTransferConfirmed should equal(formData.transferLegalOwnership)
  }

  "addFinalTransferConfirmation" should "return an error when the API has an error" in {
    val graphQlResponse = HttpError("something went wrong", StatusCode.InternalServerError)
    when(graphQlClient.getResult(token, aftc.document, Some(aftc.Variables(addFinalTransferConfirmationInput))))
      .thenReturn(Future.failed(graphQlResponse))

    val transferConfirmation = confirmTransferService.addFinalTransferConfirmation(consignmentId, token, formData).failed
      .futureValue.asInstanceOf[HttpError]

    transferConfirmation shouldBe a[HttpError]
  }

  "addFinalTransferConfirmation" should "throw an AuthorisationException if the API returns an auth error" in {
    val graphQlResponse = GraphQlResponse[aftc.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
    when(graphQlClient.getResult(token, aftc.document, Some(aftc.Variables(addFinalTransferConfirmationInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferConfirmation = confirmTransferService.addFinalTransferConfirmation(consignmentId, token, formData).failed
      .futureValue.asInstanceOf[AuthorisationException]

    transferConfirmation shouldBe a[AuthorisationException]
  }
}
