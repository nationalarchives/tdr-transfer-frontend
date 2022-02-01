package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import controllers.{TransferAgreementComplianceData, TransferAgreementData}
import errors.AuthorisationException
import graphql.codegen.AddTransferAgreementNonCompliance.{addTransferAgreementNotCompliance => atanc}
import graphql.codegen.AddTransferAgreementNonCompliance.addTransferAgreementNotCompliance.AddTransferAgreementNotCompliance
import graphql.codegen.AddTransferAgreementCompliance.{addTransferAgreementCompliance => atac}
import graphql.codegen.AddTransferAgreementCompliance.addTransferAgreementCompliance.AddTransferAgreementCompliance
import graphql.codegen.types.{AddTransferAgreementComplianceInput, AddTransferAgreementNotComplianceInput}
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

class TransferAgreementServiceSpec extends FlatSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQlConfig = mock[GraphQLConfiguration]
  private val graphQlClientForTANotCompliance = mock[GraphQLClient[atanc.Data, atanc.Variables]]
  private val graphQlClientForTACompliance = mock[GraphQLClient[atac.Data, atac.Variables]]
  when(graphQlConfig.getClient[atanc.Data, atanc.Variables]())
    .thenReturn(graphQlClientForTANotCompliance) // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.

  when(graphQlConfig.getClient[atac.Data, atac.Variables]())
    .thenReturn(graphQlClientForTACompliance) // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.

  private val transferAgreementService: TransferAgreementService = new TransferAgreementService(graphQlConfig)
  private val consignmentId = UUID.fromString("e1ca3948-ee41-4e80-85e6-2123040c135d")

  private val token = new BearerAccessToken("some-token")

  private val taNotComplianceFormData = TransferAgreementData(publicRecord = true,
    crownCopyright = true,
    english = true)

  private val taComplianceFormData = TransferAgreementComplianceData(
    droAppraisalSelection = true,
    droSensitivity = true,
    openRecords = true)
  
  private val transferAgreementNotComplianceInput = AddTransferAgreementNotComplianceInput(consignmentId,
    allPublicRecords = taNotComplianceFormData.publicRecord,
    allCrownCopyright = taNotComplianceFormData.crownCopyright,
    allEnglish = taNotComplianceFormData.english)

  private val transferAgreementComplianceInput = AddTransferAgreementComplianceInput(consignmentId,
    appraisalSelectionSignedOff = taComplianceFormData.droAppraisalSelection,
    sensitivityReviewSignedOff = taComplianceFormData.droSensitivity,
    initialOpenRecords = taComplianceFormData.openRecords)

  override def afterEach(): Unit = {
    Mockito.reset(graphQlClientForTANotCompliance)
  }

  "addTransferAgreementNotCompliance" should "return the TransferAgreement from the API" in {
    val transferAgreementNotComplianceResponse = AddTransferAgreementNotCompliance(consignmentId,
      allPublicRecords = true,
      allCrownCopyright = true,
      allEnglish = true)

    val graphQlResponse =
      GraphQlResponse(
        Some(
          atanc.Data(transferAgreementNotComplianceResponse)
        ),
        Nil) // Please ignore the "Type mismatch" error that IntelliJ displays, as it is incorrect.
    when(graphQlClientForTANotCompliance.getResult(token, atanc.document, Some(atanc.Variables(transferAgreementNotComplianceInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferAgreement: AddTransferAgreementNotCompliance =
      transferAgreementService.addTransferAgreementNotCompliance(consignmentId, token, taNotComplianceFormData).futureValue

    transferAgreement.consignmentId should equal(consignmentId)
    transferAgreement.allPublicRecords should equal(taNotComplianceFormData.publicRecord)
    transferAgreement.allCrownCopyright should equal(taNotComplianceFormData.crownCopyright)
    transferAgreement.allEnglish should equal(taNotComplianceFormData.english)
  }

  "addTransferAgreementNotCompliance" should "return an error when the API has an error" in {
    val graphQlResponse = HttpError("something went wrong", StatusCode.InternalServerError)
    when(graphQlClientForTANotCompliance.getResult(token, atanc.document, Some(atanc.Variables(transferAgreementNotComplianceInput))))
      .thenReturn(Future.failed(graphQlResponse))

    val transferAgreement = transferAgreementService.addTransferAgreementNotCompliance(consignmentId, token, taNotComplianceFormData).failed
      .futureValue.asInstanceOf[HttpError]

    transferAgreement shouldBe a[HttpError]
  }

  "addTransferAgreementNotCompliance" should "throw an AuthorisationException if the API returns an auth error" in {
    val graphQlResponse = GraphQlResponse[atanc.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
    when(graphQlClientForTANotCompliance.getResult(token, atanc.document, Some(atanc.Variables(transferAgreementNotComplianceInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferAgreement = transferAgreementService.addTransferAgreementNotCompliance(consignmentId, token, taNotComplianceFormData).failed
      .futureValue.asInstanceOf[AuthorisationException]

    transferAgreement shouldBe a[AuthorisationException]
  }

  "addTransferAgreementCompliance" should "return the TransferAgreement from the API" in {
    val transferAgreementComplianceResponse = AddTransferAgreementCompliance(consignmentId,
      appraisalSelectionSignedOff = true,
      sensitivityReviewSignedOff = true,
      initialOpenRecords = true)

    val graphQlResponse =
      GraphQlResponse(
        Some(
          atac.Data(transferAgreementComplianceResponse)
        ),
        Nil) // Please ignore the "Type mismatch" error that IntelliJ displays, as it is incorrect.
    when(graphQlClientForTACompliance.getResult(token, atac.document, Some(atac.Variables(transferAgreementComplianceInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferAgreementCompliance: AddTransferAgreementCompliance =
      transferAgreementService.addTransferAgreementCompliance(consignmentId, token, taComplianceFormData).futureValue

    transferAgreementCompliance.consignmentId should equal(consignmentId)
    transferAgreementCompliance.appraisalSelectionSignedOff should equal(taComplianceFormData.droAppraisalSelection)
    transferAgreementCompliance.sensitivityReviewSignedOff should equal(taComplianceFormData.droSensitivity)
    transferAgreementCompliance.initialOpenRecords should equal(taComplianceFormData.openRecords)
  }

  "addTransferAgreementCompliance" should "return an error when the API has an error" in {
    val graphQlResponse = HttpError("something went wrong", StatusCode.InternalServerError)
    when(graphQlClientForTACompliance.getResult(token, atac.document, Some(atac.Variables(transferAgreementComplianceInput))))
      .thenReturn(Future.failed(graphQlResponse))

    val transferAgreementCompliance= transferAgreementService.addTransferAgreementCompliance(consignmentId, token, taComplianceFormData).failed
      .futureValue.asInstanceOf[HttpError]

    transferAgreementCompliance shouldBe a[HttpError]
  }

  "addTransferAgreementCompliance" should "throw an AuthorisationException if the API returns an auth error" in {
    val graphQlResponse = GraphQlResponse[atac.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
    when(graphQlClientForTACompliance.getResult(token, atac.document, Some(atac.Variables(transferAgreementComplianceInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferAgreementCompliance= transferAgreementService.addTransferAgreementCompliance(consignmentId, token, taComplianceFormData).failed
      .futureValue.asInstanceOf[AuthorisationException]

    transferAgreementCompliance shouldBe a[AuthorisationException]
  }
}
