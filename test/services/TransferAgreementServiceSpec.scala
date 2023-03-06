package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import controllers.{TransferAgreementPart2Data, TransferAgreementData}
import errors.AuthorisationException
import graphql.codegen.AddTransferAgreementPrivateBeta.{addTransferAgreementPrivateBeta => atapb}
import graphql.codegen.AddTransferAgreementPrivateBeta.addTransferAgreementPrivateBeta.AddTransferAgreementPrivateBeta
import graphql.codegen.AddTransferAgreementCompliance.{addTransferAgreementCompliance => atac}
import graphql.codegen.AddTransferAgreementCompliance.addTransferAgreementCompliance.AddTransferAgreementCompliance
import graphql.codegen.types.{AddTransferAgreementComplianceInput, AddTransferAgreementPrivateBetaInput}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar
import sttp.client.HttpError
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class TransferAgreementServiceSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQlConfig = mock[GraphQLConfiguration]
  private val graphQlClientForTAPart1 = mock[GraphQLClient[atapb.Data, atapb.Variables]]
  private val graphQlClientForTAPart2 = mock[GraphQLClient[atac.Data, atac.Variables]]
  when(graphQlConfig.getClient[atapb.Data, atapb.Variables]())
    .thenReturn(graphQlClientForTAPart1) // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.

  when(graphQlConfig.getClient[atac.Data, atac.Variables]())
    .thenReturn(graphQlClientForTAPart2) // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.

  private val transferAgreementService: TransferAgreementService = new TransferAgreementService(graphQlConfig)
  private val consignmentId = UUID.fromString("e1ca3948-ee41-4e80-85e6-2123040c135d")

  private val token = new BearerAccessToken("some-token")

  private val taPart1FormData = TransferAgreementData(publicRecord = true, crownCopyright = true, english = Option(true))

  private val taPart2FormData = TransferAgreementPart2Data(droAppraisalSelection = true, droSensitivity = true, openRecords = Option(true))

  private val transferAgreementPart1Input = AddTransferAgreementPrivateBetaInput(
    consignmentId,
    allPublicRecords = taPart1FormData.publicRecord,
    allCrownCopyright = taPart1FormData.crownCopyright,
    allEnglish = taPart1FormData.english
  )

  private val transferAgreementPart2Input = AddTransferAgreementComplianceInput(
    consignmentId,
    appraisalSelectionSignedOff = taPart2FormData.droAppraisalSelection,
    sensitivityReviewSignedOff = taPart2FormData.droSensitivity,
    initialOpenRecords = taPart2FormData.openRecords
  )

  override def afterEach(): Unit = {
    Mockito.reset(graphQlClientForTAPart1)
  }

  "addTransferAgreementPart1" should "return the TransferAgreement from the API" in {
    val transferAgreementPart1Response = AddTransferAgreementPrivateBeta(consignmentId, allPublicRecords = true, allCrownCopyright = true, allEnglish = Option(true))

    val graphQlResponse =
      GraphQlResponse(
        Some(
          atapb.Data(transferAgreementPart1Response)
        ),
        Nil
      ) // Please ignore the "Type mismatch" error that IntelliJ displays, as it is incorrect.
    when(graphQlClientForTAPart1.getResult(token, atapb.document, Some(atapb.Variables(transferAgreementPart1Input))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferAgreement: AddTransferAgreementPrivateBeta =
      transferAgreementService.addTransferAgreementPart1(consignmentId, token, taPart1FormData).futureValue

    transferAgreement.consignmentId should equal(consignmentId)
    transferAgreement.allPublicRecords should equal(taPart1FormData.publicRecord)
    transferAgreement.allCrownCopyright should equal(taPart1FormData.crownCopyright)
    transferAgreement.allEnglish should equal(taPart1FormData.english)
  }

  "addTransferAgreementPart1" should "return an error when the API has an error" in {
    val graphQlResponse = HttpError("something went wrong", StatusCode.InternalServerError)
    when(graphQlClientForTAPart1.getResult(token, atapb.document, Some(atapb.Variables(transferAgreementPart1Input))))
      .thenReturn(Future.failed(graphQlResponse))

    val transferAgreement = transferAgreementService.addTransferAgreementPart1(consignmentId, token, taPart1FormData).failed.futureValue.asInstanceOf[HttpError]

    transferAgreement shouldBe a[HttpError]
  }

  "addTransferAgreementPart1" should "throw an AuthorisationException if the API returns an auth error" in {
    val graphQlResponse = GraphQlResponse[atapb.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
    when(graphQlClientForTAPart1.getResult(token, atapb.document, Some(atapb.Variables(transferAgreementPart1Input))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferAgreement =
      transferAgreementService.addTransferAgreementPart1(consignmentId, token, taPart1FormData).failed.futureValue.asInstanceOf[AuthorisationException]

    transferAgreement shouldBe a[AuthorisationException]
  }

  "addTransferAgreementPart2" should "return the TransferAgreement from the API" in {
    val transferAgreementPart2Response =
      AddTransferAgreementCompliance(consignmentId, appraisalSelectionSignedOff = true, sensitivityReviewSignedOff = true, Option(true))

    val graphQlResponse =
      GraphQlResponse(
        Some(
          atac.Data(transferAgreementPart2Response)
        ),
        Nil
      ) // Please ignore the "Type mismatch" error that IntelliJ displays, as it is incorrect.
    when(graphQlClientForTAPart2.getResult(token, atac.document, Some(atac.Variables(transferAgreementPart2Input))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferAgreementPart2: AddTransferAgreementCompliance =
      transferAgreementService.addTransferAgreementPart2(consignmentId, token, taPart2FormData).futureValue

    transferAgreementPart2.consignmentId should equal(consignmentId)
    transferAgreementPart2.appraisalSelectionSignedOff should equal(taPart2FormData.droAppraisalSelection)
    transferAgreementPart2.sensitivityReviewSignedOff should equal(taPart2FormData.droSensitivity)
    transferAgreementPart2.initialOpenRecords should equal(taPart2FormData.openRecords)
  }

  "addTransferAgreementPart2" should "return an error when the API has an error" in {
    val graphQlResponse = HttpError("something went wrong", StatusCode.InternalServerError)
    when(graphQlClientForTAPart2.getResult(token, atac.document, Some(atac.Variables(transferAgreementPart2Input))))
      .thenReturn(Future.failed(graphQlResponse))

    val transferAgreementPart2 = transferAgreementService.addTransferAgreementPart2(consignmentId, token, taPart2FormData).failed.futureValue.asInstanceOf[HttpError]

    transferAgreementPart2 shouldBe a[HttpError]
  }

  "addTransferAgreementPart2" should "throw an AuthorisationException if the API returns an auth error" in {
    val graphQlResponse = GraphQlResponse[atac.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
    when(graphQlClientForTAPart2.getResult(token, atac.document, Some(atac.Variables(transferAgreementPart2Input))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferAgreementPart2 =
      transferAgreementService.addTransferAgreementPart2(consignmentId, token, taPart2FormData).failed.futureValue.asInstanceOf[AuthorisationException]

    transferAgreementPart2 shouldBe a[AuthorisationException]
  }
}
