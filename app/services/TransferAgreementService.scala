package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.IsTransferAgreementComplete.{isTransferAgreementComplete => itac}
import javax.inject.Inject
import services.ApiErrorHandling.sendApiRequest

import scala.concurrent.{ExecutionContext, Future}

class TransferAgreementService @Inject()(val graphqlConfiguration: GraphQLConfiguration)
                                        (implicit ec: ExecutionContext) {

  private val isTransferAgreementCompleteClient = graphqlConfiguration.getClient[itac.Data, itac.Variables]()

  def transferAgreementExists(consignmentId: UUID, token: BearerAccessToken): Future[Boolean] = {
    val variables = itac.Variables(consignmentId)

    sendApiRequest(isTransferAgreementCompleteClient, itac.document, token, variables)
      .map(data => data.getTransferAgreement.exists(_.isAgreementComplete))
  }
}
