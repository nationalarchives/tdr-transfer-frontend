package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import controllers.{TransferAgreementData, TransferAgreementComplianceData}
import graphql.codegen.AddTransferAgreementNonCompliance.{addTransferAgreementNotCompliance => atanc}
import graphql.codegen.AddTransferAgreementCompliance.{addTransferAgreementCompliance => atac}
import graphql.codegen.types.{AddTransferAgreementComplianceInput, AddTransferAgreementNotComplianceInput}
import services.ApiErrorHandling.sendApiRequest

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TransferAgreementService @Inject()(val graphqlConfiguration: GraphQLConfiguration) (implicit val ec: ExecutionContext){
  private val addTransferAgreementNotComplianceClient =
    graphqlConfiguration.getClient[atanc.Data, atanc.Variables]() // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.
  private val addTransferAgreementComplianceClient =
    graphqlConfiguration.getClient[atac.Data, atac.Variables]() // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.
  def addTransferAgreementNotCompliance(consignmentId: UUID,
                                        token: BearerAccessToken,
                                        formData: TransferAgreementData): Future[atanc.AddTransferAgreementNotCompliance] = {
    val addTransferAgreementNotComplianceInput: AddTransferAgreementNotComplianceInput = AddTransferAgreementNotComplianceInput(consignmentId,
      formData.publicRecord,
      formData.crownCopyright,
      formData.english
    )
    val variables: atanc.Variables = atanc.Variables(addTransferAgreementNotComplianceInput)

    sendApiRequest(addTransferAgreementNotComplianceClient, atanc.document, token, variables).map(_.addTransferAgreementNotCompliance)
  }

  def addTransferAgreementCompliance(consignmentId: UUID,
                                     token: BearerAccessToken,
                                     formData: TransferAgreementComplianceData): Future[atac.AddTransferAgreementCompliance] = {
    val addTransferAgreementComplianceInput: AddTransferAgreementComplianceInput = AddTransferAgreementComplianceInput(consignmentId,
      formData.droAppraisalSelection,
      formData.openRecords,
      formData.droSensitivity
    )
    val variables: atac.Variables = atac.Variables(addTransferAgreementComplianceInput)

    sendApiRequest(addTransferAgreementComplianceClient, atac.document, token, variables).map(_.addTransferAgreementCompliance)
  }
}
