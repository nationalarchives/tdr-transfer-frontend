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
  private val addFinalJudgmentTransferConfirmationClient: GraphQLClient[aftc.Data, aftc.Variables] =
    graphqlConfiguration.getClient[aftc.Data, aftc.Variables]() // Please ignore the Implicit-related error that IntelliJ displays, as it is incorrect.

  def addFinalTransferConfirmation(consignmentId: UUID, token: BearerAccessToken, formData: FinalTransferConfirmationData): Future[aftc.AddFinalTransferConfirmation] = {
    val addFinalTransferConfirmationInput: AddFinalTransferConfirmationInput =
      AddFinalTransferConfirmationInput(consignmentId, legalCustodyTransferConfirmed = formData.transferLegalCustody)
    val variables: aftc.Variables = aftc.Variables(addFinalTransferConfirmationInput)
    println(s"addFinalTransferConfirmationClient: $addFinalTransferConfirmationClient")
    println(s"variables: $variables")
    sendApiRequest(addFinalTransferConfirmationClient, aftc.document, token, variables).map(_.addFinalTransferConfirmation)
  }

  def addFinalJudgmentTransferConfirmation(consignmentId: UUID, token: BearerAccessToken): Future[aftc.AddFinalTransferConfirmation] = {
    val addFinalJudgmentTransferConfirmationInput: AddFinalTransferConfirmationInput =
      AddFinalTransferConfirmationInput(consignmentId, legalCustodyTransferConfirmed = true)

    val variables: aftc.Variables = aftc.Variables(addFinalJudgmentTransferConfirmationInput)
    println(s"judgment vers : ${addFinalJudgmentTransferConfirmationInput}")
    println(s"jvariables: $variables")
    sendApiRequest(addFinalJudgmentTransferConfirmationClient, aftc.document, token, variables).map(_.addFinalTransferConfirmation)
  }

}