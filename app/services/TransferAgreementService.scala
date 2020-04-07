package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.IsTransferAgreementComplete.{isTransferAgreementComplete => itac}
import javax.inject.Inject
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError

import scala.concurrent.{ExecutionContext, Future}

class TransferAgreementService @Inject()(val graphqlConfiguration: GraphQLConfiguration)
                                        (implicit ec: ExecutionContext) {

  private val isTransferAgreementCompleteClient = graphqlConfiguration.getClient[itac.Data, itac.Variables]()

  def transferAgreementExists(consignmentId: UUID, token: BearerAccessToken): Future[Boolean] = {
    val variables = itac.Variables(consignmentId)

    isTransferAgreementCompleteClient.getResult(token, itac.document, Some(variables)).map(graphQlResponse => {
      graphQlResponse.errors match {
        case Nil => graphQlResponse.data.flatMap(_.getTransferAgreement).exists(_.isAgreementComplete)
        case List(authError: NotAuthorisedError) => throw new AuthorisationException(authError.message)
        case errors => throw new RuntimeException(errors.map(e => e.message).mkString)
      }
    })
  }
}
