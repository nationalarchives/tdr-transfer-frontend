package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import controllers.TransferAgreementData
import graphql.codegen.AddTransferAgreement.AddTransferAgreement
import graphql.codegen.types.AddTransferAgreementInput
import services.ApiErrorHandling.sendApiRequest

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TransferAgreementService @Inject()(val graphqlConfiguration: GraphQLConfiguration) (implicit val ec: ExecutionContext){
  private val addTransferAgreementClient = graphqlConfiguration.getClient[AddTransferAgreement.Data, AddTransferAgreement.Variables]()
  def addTransferAgreement(consignmentId: UUID, token: BearerAccessToken, formData: TransferAgreementData): Future[Any] = {
    val addTransferAgreementInput: AddTransferAgreementInput = AddTransferAgreementInput(consignmentId,
      formData.publicRecord,
      formData.crownCopyright,
      formData.english,
      formData.droAppraisalSelection,
      formData.openRecords,
      formData.droSensitivity
    )
    val variables: AddTransferAgreement.Variables = AddTransferAgreement.Variables(addTransferAgreementInput)

    sendApiRequest(addTransferAgreementClient, AddTransferAgreement.document, token, variables).map(_.addTransferAgreement)
  }
}
