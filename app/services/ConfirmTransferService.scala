package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import controllers.FinalTransferConfirmationData
import graphql.codegen.AddFinalTransferConfirmation.{addFinalTransferConfirmation => aftc}
import graphql.codegen.types.AddFinalTransferConfirmationInput
import services.ApiErrorHandling.sendApiRequest
import uk.gov.nationalarchives.tdr.GraphQLClient

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfirmTransferService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {
  private val addFinalTransferConfirmationClient: GraphQLClient[aftc.Data, aftc.Variables] =
    graphqlConfiguration.getClient[aftc.Data, aftc.Variables]() // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.

  def addFinalTransferConfirmation(consignmentId: UUID, token: BearerAccessToken, formData: FinalTransferConfirmationData): Future[aftc.AddFinalTransferConfirmation] = {
    val addFinalTransferConfirmationInput: AddFinalTransferConfirmationInput =
      AddFinalTransferConfirmationInput(consignmentId, formData.transferLegalCustody)
    val variables: aftc.Variables = aftc.Variables(addFinalTransferConfirmationInput)
    sendApiRequest(addFinalTransferConfirmationClient, aftc.document, token, variables).map(_.addFinalTransferConfirmation)
  }
}
