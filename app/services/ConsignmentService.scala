package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.AddConsignment.addConsignment
import graphql.codegen.GetConsignment.getConsignment
import graphql.codegen.GetConsignmentFolderDetails.getConsignmentFolderDetails
import graphql.codegen.GetConsignmentReference.getConsignmentReference
import graphql.codegen.GetConsignmentSummary.getConsignmentSummary
import graphql.codegen.GetFileCheckProgress.getFileCheckProgress
import graphql.codegen.types.AddConsignmentInput
import graphql.codegen.{AddConsignment, GetConsignment, GetFileCheckProgress}
import javax.inject.{Inject, Singleton}
import services.ApiErrorHandling._
import uk.gov.nationalarchives.tdr.keycloak.Token

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConsignmentService @Inject()(val graphqlConfiguration: GraphQLConfiguration)
                                  (implicit val ec: ExecutionContext) {

  private val getConsignmentClient = graphqlConfiguration.getClient[getConsignment.Data, getConsignment.Variables]()
  private val addConsignmentClient = graphqlConfiguration.getClient[addConsignment.Data, addConsignment.Variables]()
  private val getConsignmentFileCheckClient = graphqlConfiguration.getClient[getFileCheckProgress.Data, getFileCheckProgress.Variables]()
  private val getConsignmentFolderDetailsClient = graphqlConfiguration.getClient[getConsignmentFolderDetails.Data, getConsignmentFolderDetails.Variables]()
  private val getConsignmentSummaryClient = graphqlConfiguration.getClient[getConsignmentSummary.Data, getConsignmentSummary.Variables]()
  private val getConsignmentReferenceClient = graphqlConfiguration.getClient[getConsignmentReference.Data, getConsignmentReference.Variables]()

  def consignmentExists(consignmentId: UUID,
                        token: BearerAccessToken): Future[Boolean] = {
    val variables: getConsignment.Variables = new GetConsignment.getConsignment.Variables(consignmentId)

    sendApiRequest(getConsignmentClient, getConsignment.document, token, variables)
      .map(data => data.getConsignment.isDefined)
  }

  def createConsignment(seriesId: Option[UUID], token: Token): Future[addConsignment.AddConsignment] = {
    val consignmentType: String = if (token.isJudgmentUser) { "judgment" } else { "standard" }
    val addConsignmentInput: AddConsignmentInput = AddConsignmentInput(seriesId, consignmentType)
    val variables: addConsignment.Variables = AddConsignment.addConsignment.Variables(addConsignmentInput)

    sendApiRequest(addConsignmentClient, addConsignment.document, token.bearerAccessToken, variables)
      .map(data => data.addConsignment)
  }

  def getConsignmentFileChecks(consignmentId: UUID, token: BearerAccessToken): Future[getFileCheckProgress.GetConsignment] = {
    val variables: getFileCheckProgress.Variables = new GetFileCheckProgress.getFileCheckProgress.Variables(consignmentId)

    sendApiRequest(getConsignmentFileCheckClient, getFileCheckProgress.document, token, variables)
      .map(data => data.getConsignment.get)
  }

  def getConsignmentFolderInfo(consignmentId: UUID, token: BearerAccessToken): Future[getConsignmentFolderDetails.GetConsignment] = {
    val variables: getConsignmentFolderDetails.Variables = new getConsignmentFolderDetails.Variables(consignmentId)

    sendApiRequest(getConsignmentFolderDetailsClient, getConsignmentFolderDetails.document, token, variables)
      .map(data => data.getConsignment.get)
  }

  def getConsignmentConfirmTransfer(consignmentId: UUID, token: BearerAccessToken): Future[getConsignmentSummary.GetConsignment] = {
    val variables: getConsignmentSummary.Variables = new getConsignmentSummary.Variables(consignmentId)

    sendApiRequest(getConsignmentSummaryClient, getConsignmentSummary.document, token, variables)
      .map(data => data.getConsignment.get)
  }

  def getConsignmentRef(consignmentId: UUID, token: BearerAccessToken): Future[getConsignmentReference.GetConsignment] = {
    val variables: getConsignmentReference.Variables = new getConsignmentReference.Variables(consignmentId)

    sendApiRequest(getConsignmentReferenceClient, getConsignmentReference.document, token, variables)
      .map(data => data.getConsignment.get)
  }
}
