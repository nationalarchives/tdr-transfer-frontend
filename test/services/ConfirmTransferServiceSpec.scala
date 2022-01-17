package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import controllers.FinalTransferConfirmationData
import errors.AuthorisationException
import graphql.codegen.AddFinalJudgmentTransferConfirmation.addFinalJudgmentTransferConfirmation.AddFinalJudgmentTransferConfirmation
import graphql.codegen.AddFinalJudgmentTransferConfirmation.{addFinalJudgmentTransferConfirmation => afjtc}
import graphql.codegen.AddFinalTransferConfirmation.addFinalTransferConfirmation.AddFinalTransferConfirmation
import graphql.codegen.AddFinalTransferConfirmation.{addFinalTransferConfirmation => aftc}
import graphql.codegen.types.{AddFinalJudgmentTransferConfirmationInput, AddFinalTransferConfirmationInput}
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
  private val standardGraphQlClient = mock[GraphQLClient[aftc.Data, aftc.Variables]]
  when(graphQlConfig.getClient[aftc.Data, aftc.Variables]())
    .thenReturn(standardGraphQlClient) // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.
  private val judgmentGraphQlClient = mock[GraphQLClient[afjtc.Data, afjtc.Variables]]
  when(graphQlConfig.getClient[afjtc.Data, afjtc.Variables]())
    .thenReturn(judgmentGraphQlClient) // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.

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

  val addFinalJudgmentTransferConfirmationInput: AddFinalJudgmentTransferConfirmationInput =
    AddFinalJudgmentTransferConfirmationInput(consignmentId, legalCustodyTransferConfirmed = true)

  override def afterEach(): Unit = {
    Mockito.reset(standardGraphQlClient)
    Mockito.reset(judgmentGraphQlClient)
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
    when(standardGraphQlClient.getResult(token, aftc.document, Some(aftc.Variables(addFinalTransferConfirmationInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferConfirmation: AddFinalTransferConfirmation =
      confirmTransferService.addFinalTransferConfirmation(consignmentId, token, formData).futureValue

    transferConfirmation.consignmentId should equal(consignmentId)
    transferConfirmation.finalOpenRecordsConfirmed should equal(formData.openRecords)
    transferConfirmation.legalOwnershipTransferConfirmed should equal(formData.transferLegalOwnership)
  }

  "addFinalTransferConfirmation" should "return an error when the API has an error" in {
    val graphQlResponse = HttpError("something went wrong", StatusCode.InternalServerError)
    when(standardGraphQlClient.getResult(token, aftc.document, Some(aftc.Variables(addFinalTransferConfirmationInput))))
      .thenReturn(Future.failed(graphQlResponse))

    val transferConfirmation = confirmTransferService.addFinalTransferConfirmation(consignmentId, token, formData).failed
      .futureValue.asInstanceOf[HttpError]

    transferConfirmation shouldBe a[HttpError]
  }

  "addFinalTransferConfirmation" should "throw an AuthorisationException if the API returns an auth error" in {
    val graphQlResponse = GraphQlResponse[aftc.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
    when(standardGraphQlClient.getResult(token, aftc.document, Some(aftc.Variables(addFinalTransferConfirmationInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferConfirmation = confirmTransferService.addFinalTransferConfirmation(consignmentId, token, formData).failed
      .futureValue.asInstanceOf[AuthorisationException]

    transferConfirmation shouldBe a[AuthorisationException]
  }

  "addFinalJudgmentTransferConfirmation" should "return the TransferConfirmation from the API" in {
    val finalJudgmentTransferConfirmationResponse = AddFinalJudgmentTransferConfirmation(
      consignmentId,
      legalCustodyTransferConfirmed = true)

    val graphQlResponse =
      GraphQlResponse(Some(afjtc.Data(
        finalJudgmentTransferConfirmationResponse
      )), Nil) // Please ignore the "Type mismatch" error that IntelliJ displays, as it is incorrect.
    when(judgmentGraphQlClient.getResult(token, afjtc.document, Some(afjtc.Variables(addFinalJudgmentTransferConfirmationInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferConfirmation: AddFinalJudgmentTransferConfirmation =
      confirmTransferService.addFinalJudgmentTransferConfirmation(consignmentId, token).futureValue

    transferConfirmation.consignmentId should equal(consignmentId)
    transferConfirmation.legalCustodyTransferConfirmed should equal(true)
  }

  "addFinalJudgmentTransferConfirmation" should "return an error when the API has an error" in {
    val graphQlResponse = HttpError("something went wrong", StatusCode.InternalServerError)
    when(judgmentGraphQlClient.getResult(token, afjtc.document, Some(afjtc.Variables(addFinalJudgmentTransferConfirmationInput))))
      .thenReturn(Future.failed(graphQlResponse))

    val transferConfirmation = confirmTransferService.addFinalJudgmentTransferConfirmation(consignmentId, token).failed
      .futureValue.asInstanceOf[HttpError]

    transferConfirmation shouldBe a[HttpError]
  }

  "addFinalJudgmentTransferConfirmation" should "throw an AuthorisationException if the API returns an auth error" in {
    val graphQlResponse = GraphQlResponse[afjtc.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
    when(judgmentGraphQlClient.getResult(token, afjtc.document, Some(afjtc.Variables(addFinalJudgmentTransferConfirmationInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferConfirmation = confirmTransferService.addFinalJudgmentTransferConfirmation(consignmentId, token).failed
      .futureValue.asInstanceOf[AuthorisationException]

    transferConfirmation shouldBe a[AuthorisationException]
  }
}
