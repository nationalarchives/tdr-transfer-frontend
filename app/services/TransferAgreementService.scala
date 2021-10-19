package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import controllers.TransferAgreementData
import graphql.codegen.AddTransferAgreement.{AddTransferAgreement => ata}
import graphql.codegen.types.AddTransferAgreementInput
import services.ApiErrorHandling.sendApiRequest

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TransferAgreementService @Inject()(val graphqlConfiguration: GraphQLConfiguration) (implicit val ec: ExecutionContext){
  private val addTransferAgreementClient =
    graphqlConfiguration.getClient[ata.Data, ata.Variables]() // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.
  def addTransferAgreement(consignmentId: UUID, token: BearerAccessToken, formData: TransferAgreementData): Future[Any] = {
    val addTransferAgreementInput: AddTransferAgreementInput = AddTransferAgreementInput(consignmentId,
      formData.publicRecord,
      formData.crownCopyright,
      formData.english,
      formData.droAppraisalSelection,
      formData.openRecords,
      formData.droSensitivity
    )
    val variables: ata.Variables = ata.Variables(addTransferAgreementInput)

    sendApiRequest(addTransferAgreementClient, ata.document, token, variables).map(_.addTransferAgreement)
  }
}
