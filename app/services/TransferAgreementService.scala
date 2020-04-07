package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.IsTransferAgreementComplete.{isTransferAgreementComplete => itac}
import javax.inject.Inject

import scala.concurrent.{ExecutionContext, Future}

class TransferAgreementService @Inject()(val graphqlConfiguration: GraphQLConfiguration)
                                        (implicit ec: ExecutionContext) {

  private val isTransferAgreementCompleteClient = graphqlConfiguration.getClient[itac.Data, itac.Variables]()

  def transferAgreementExists(consignmentId: UUID, token: BearerAccessToken): Future[Boolean] = {
    val variables = itac.Variables(consignmentId)

    isTransferAgreementCompleteClient.getResult(token, itac.document, Some(variables)).map(data => {
      val isComplete: Option[Boolean] = for {
        dataDefined <- data.data
        transferAgreement <- dataDefined.getTransferAgreement
      } yield transferAgreement.isAgreementComplete

      isComplete.isDefined && isComplete.get
    })
  }
}
