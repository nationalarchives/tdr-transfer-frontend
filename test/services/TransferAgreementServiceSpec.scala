package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import controllers.TransferAgreementData
import errors.AuthorisationException
import graphql.codegen.AddTransferAgreement.{addTransferAgreement => ata}
import graphql.codegen.AddTransferAgreement.addTransferAgreement.AddTransferAgreement
import graphql.codegen.types.AddTransferAgreementInput
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
  private val graphQlClient = mock[GraphQLClient[ata.Data, ata.Variables]]
  when(graphQlConfig.getClient[ata.Data, ata.Variables]())
    .thenReturn(graphQlClient) // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.

  private val transferAgreementService: TransferAgreementService = new TransferAgreementService(graphQlConfig)
  private val consignmentId = UUID.fromString("e1ca3948-ee41-4e80-85e6-2123040c135d")

  private val token = new BearerAccessToken("some-token")

  private val formData = TransferAgreementData(publicRecord = true,
    crownCopyright = true,
    english = true,
    droAppraisalSelection = true,
    droSensitivity = true,
    openRecords = true)

  private val transferAgreementInput = AddTransferAgreementInput(consignmentId,
    allPublicRecords = formData.publicRecord,
    allCrownCopyright = formData.crownCopyright,
    allEnglish = formData.english,
    appraisalSelectionSignedOff = formData.droAppraisalSelection,
    initialOpenRecords = formData.openRecords,
    sensitivityReviewSignedOff = formData.droSensitivity)

  override def afterEach(): Unit = {
    Mockito.reset(graphQlClient)
  }

  "addTransferAgreement" should "return the TransferAgreement from the API" in {
    val transferAgreementResponse = AddTransferAgreement(consignmentId,
      allPublicRecords = true,
      allCrownCopyright = true,
      allEnglish = true,
      appraisalSelectionSignedOff = true,
      sensitivityReviewSignedOff = true,
      initialOpenRecords = true)

    val graphQlResponse =
      GraphQlResponse(Some(ata.Data(transferAgreementResponse)), Nil) // Please ignore the "Type mismatch" error that IntelliJ displays, as it is incorrect.
    when(graphQlClient.getResult(token, ata.document, Some(ata.Variables(transferAgreementInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferAgreement: AddTransferAgreement =
      transferAgreementService.addTransferAgreement(consignmentId, token, formData).futureValue

    transferAgreement.consignmentId should equal(consignmentId)
    transferAgreement.allCrownCopyright should equal(formData.crownCopyright)
    transferAgreement.allEnglish should equal(formData.english)
    transferAgreement.appraisalSelectionSignedOff should equal(formData.droAppraisalSelection)
    transferAgreement.sensitivityReviewSignedOff should equal(formData.droSensitivity)
    transferAgreement.initialOpenRecords should equal(formData.openRecords)
  }

  "addTransferAgreement" should "return an error when the API has an error" in {
    val graphQlResponse = HttpError("something went wrong", StatusCode.InternalServerError)
    when(graphQlClient.getResult(token, ata.document, Some(ata.Variables(transferAgreementInput))))
      .thenReturn(Future.failed(graphQlResponse))

    val transferAgreement= transferAgreementService.addTransferAgreement(consignmentId, token, formData).failed
      .futureValue.asInstanceOf[HttpError]

    transferAgreement shouldBe a[HttpError]
  }

  "addTransferAgreement" should "throw an AuthorisationException if the API returns an auth error" in {
    val graphQlResponse = GraphQlResponse[ata.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
    when(graphQlClient.getResult(token, ata.document, Some(ata.Variables(transferAgreementInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val transferAgreement= transferAgreementService.addTransferAgreement(consignmentId, token, formData).failed
      .futureValue.asInstanceOf[AuthorisationException]

    transferAgreement shouldBe a[AuthorisationException]
  }
}
