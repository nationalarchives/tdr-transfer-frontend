package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import controllers.FinalTransferConfirmationData
import graphql.codegen.AddFinalTransferConfirmation.{addFinalTransferConfirmation => aftc}
import uk.gov.nationalarchives.tdr.GraphQLClient

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConfirmTransferService @Inject() (val dynamoService: DynamoService)(implicit val ec: ExecutionContext) {

  def addFinalTransferConfirmation(consignmentId: UUID, formData: FinalTransferConfirmationData): Future[aftc.AddFinalTransferConfirmation] = {
    dynamoService.setFieldToValue(consignmentId, "status_LegalCustodyTransferConfirmed", formData.transferLegalCustody).map(_ => aftc.AddFinalTransferConfirmation(consignmentId, formData.transferLegalCustody))
  }
}
