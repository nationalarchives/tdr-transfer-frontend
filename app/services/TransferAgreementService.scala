package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import controllers.{TransferAgreementData, TransferAgreementPart2Data}
import graphql.codegen.AddTransferAgreementPrivateBeta.{addTransferAgreementPrivateBeta => atapb}
import graphql.codegen.AddTransferAgreementCompliance.{addTransferAgreementCompliance => atac}
import graphql.codegen.types.{AddTransferAgreementComplianceInput, AddTransferAgreementPrivateBetaInput}
import services.ApiErrorHandling.sendApiRequest

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TransferAgreementService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val addTransferAgreementPrivateBetaClient =
    graphqlConfiguration.getClient[atapb.Data, atapb.Variables]() // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.
  private val addTransferAgreementPart2Client =
    graphqlConfiguration.getClient[atac.Data, atac.Variables]() // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.
  def addTransferAgreementPrivateBeta(consignmentId: UUID, token: BearerAccessToken, formData: TransferAgreementData): Future[atapb.AddTransferAgreementPrivateBeta] = {
    val addTransferAgreementPrivateBetaInput: AddTransferAgreementPrivateBetaInput =
      AddTransferAgreementPrivateBetaInput(consignmentId, formData.publicRecord, formData.crownCopyright, formData.english)
    val variables: atapb.Variables = atapb.Variables(addTransferAgreementPrivateBetaInput)

    sendApiRequest(addTransferAgreementPrivateBetaClient, atapb.document, token, variables).map(_.addTransferAgreementPrivateBeta)
  }

  def addTransferAgreementPart2(consignmentId: UUID, token: BearerAccessToken, formData: TransferAgreementPart2Data): Future[atac.AddTransferAgreementCompliance] = {
    val addTransferAgreementComplianceInput: AddTransferAgreementComplianceInput =
      AddTransferAgreementComplianceInput(consignmentId, formData.droAppraisalSelection, formData.openRecords, formData.droSensitivity)
    val variables: atac.Variables = atac.Variables(addTransferAgreementComplianceInput)

    sendApiRequest(addTransferAgreementPart2Client, atac.document, token, variables).map(_.addTransferAgreementCompliance)
  }
}
