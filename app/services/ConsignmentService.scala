package services

import java.util.UUID
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.AddConsignment.addConsignment
import graphql.codegen.GetConsignment.getConsignment
import graphql.codegen.GetConsignmentFiles.getConsignmentFiles
import graphql.codegen.GetConsignmentFolderDetails.getConsignmentFolderDetails
import graphql.codegen.GetConsignmentReference.getConsignmentReference
import graphql.codegen.GetConsignmentExport.getConsignmentForExport
import graphql.codegen.GetConsignmentSummary.getConsignmentSummary
import graphql.codegen.GetConsignmentType.{getConsignmentType => gct}
import graphql.codegen.GetFileCheckProgress.{getFileCheckProgress => gfcp}
import graphql.codegen.GetFileCheckProgress.getFileCheckProgress
import graphql.codegen.UpdateConsignmentSeriesId.updateConsignmentSeriesId
import graphql.codegen.types.{AddConsignmentInput, UpdateConsignmentSeriesIdInput}
import graphql.codegen.{AddConsignment, GetConsignment, GetFileCheckProgress}

import javax.inject.{Inject, Singleton}
import services.ApiErrorHandling._
import uk.gov.nationalarchives.tdr.keycloak.Token

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConsignmentService @Inject()(val graphqlConfiguration: GraphQLConfiguration)
                                  (implicit val ec: ExecutionContext) {

  private val getFileCheckProgressClient = graphqlConfiguration.getClient[gfcp.Data, gfcp.Variables]()
  private val getConsignmentClient = graphqlConfiguration.getClient[getConsignment.Data, getConsignment.Variables]()
  private val addConsignmentClient = graphqlConfiguration.getClient[addConsignment.Data, addConsignment.Variables]()
  private val getConsignmentFileCheckClient = graphqlConfiguration.getClient[getFileCheckProgress.Data, getFileCheckProgress.Variables]()
  private val getConsignmentFolderDetailsClient = graphqlConfiguration.getClient[getConsignmentFolderDetails.Data, getConsignmentFolderDetails.Variables]()
  private val getConsignmentSummaryClient = graphqlConfiguration.getClient[getConsignmentSummary.Data, getConsignmentSummary.Variables]()
  private val getConsignmentReferenceClient = graphqlConfiguration.getClient[getConsignmentReference.Data, getConsignmentReference.Variables]()
  private val getConsignmentFilesClient = graphqlConfiguration.getClient[getConsignmentFiles.Data, getConsignmentFiles.Variables]()
  private val getConsignmentExportClient = graphqlConfiguration.getClient[getConsignmentForExport.Data, getConsignmentForExport.Variables]()
  private val updateConsignmentSeriesIdClient = graphqlConfiguration.getClient[updateConsignmentSeriesId.Data, updateConsignmentSeriesId.Variables]()
  private val gctClient = graphqlConfiguration.getClient[gct.Data, gct.Variables]()


  def fileCheckProgress(consignmentId: UUID, token: BearerAccessToken): Future[gfcp.GetConsignment] = {
    val variables = gfcp.Variables(consignmentId)
    sendApiRequest(getFileCheckProgressClient, gfcp.document, token, variables).map(data => {
      data.getConsignment match {
        case Some(progress) => progress
        case None => throw new RuntimeException(s"No data found for file checks for consignment $consignmentId")
      }
    })
  }

  def getConsignmentDetails(consignmentId: UUID, token: BearerAccessToken): Future[getConsignment.GetConsignment] = {
    val variables: getConsignment.Variables = new GetConsignment.getConsignment.Variables(consignmentId)

    sendApiRequest(getConsignmentClient, getConsignment.document, token, variables)
      .map(data => data.getConsignment match {
        case Some(consignment) => consignment
        case None => throw new IllegalStateException(s"No consignment found for consignment $consignmentId")
      })
  }

  def consignmentExists(consignmentId: UUID,
                        token: BearerAccessToken): Future[Boolean] = {
    val variables: getConsignment.Variables = new GetConsignment.getConsignment.Variables(consignmentId)

    sendApiRequest(getConsignmentClient, getConsignment.document, token, variables)
      .map(data => data.getConsignment.isDefined)
  }

  def getConsignmentType(consignmentId: UUID, token: BearerAccessToken): Future[String] = {
    sendApiRequest(gctClient, gct.document, token, gct.Variables(consignmentId))
      .map(data => data.getConsignment.flatMap(_.consignmentType)
        .getOrElse(throw new IllegalStateException(s"No consignment type found for consignment $consignmentId")))
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

  def getConsignmentRef(consignmentId: UUID, token: BearerAccessToken): Future[String] = {
    val variables: getConsignmentReference.Variables = new getConsignmentReference.Variables(consignmentId)

    sendApiRequest(getConsignmentReferenceClient, getConsignmentReference.document, token, variables)
      .map(data => data.getConsignment.get.consignmentReference)
  }

  def getConsignmentFilePath(consignmentId: UUID, token: BearerAccessToken): Future[getConsignmentFiles.GetConsignment] = {
    val variables: getConsignmentFiles.Variables = new getConsignmentFiles.Variables(consignmentId)

    sendApiRequest(getConsignmentFilesClient, getConsignmentFiles.document, token, variables)
      .map(data => data.getConsignment.get)
  }

  def updateSeriesIdOfConsignment(consignmentId: UUID, seriesId: UUID, token: BearerAccessToken): Future[Boolean] = {
    val updateConsignmentSeriesIdInput = UpdateConsignmentSeriesIdInput(consignmentId, seriesId)
    val variables = new updateConsignmentSeriesId.Variables(updateConsignmentSeriesIdInput)

    sendApiRequest(updateConsignmentSeriesIdClient, updateConsignmentSeriesId.document, token, variables)
      .map(data => data.updateConsignmentSeriesId.isDefined)
  }

  def getConsignmentExport(consignmentId: UUID, token: BearerAccessToken): Future[getConsignmentForExport.GetConsignment] = {
    val variables: getConsignmentForExport.Variables = new getConsignmentForExport.Variables(consignmentId)

    sendApiRequest(getConsignmentExportClient, getConsignmentForExport.document, token, variables)
      .map(data => data.getConsignment.get)
  }
}
