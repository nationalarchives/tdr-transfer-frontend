package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import controllers.util.MetadataProperty.{closureType, fileType}
import graphql.codegen.AddConsignment.addConsignment
import graphql.codegen.GetConsignment.getConsignment
import graphql.codegen.GetConsignmentDetailsForMetadataReview.{getConsignmentDetailsForMetadataReview => gcdfmr}
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
import graphql.codegen.GetConsignmentFolderDetails.getConsignmentFolderDetails
import graphql.codegen.GetConsignmentReference.getConsignmentReference
import graphql.codegen.GetConsignmentSummary.getConsignmentSummary
import graphql.codegen.GetConsignmentType.{getConsignmentType => gct}
import graphql.codegen.GetConsignments.getConsignments.Consignments
import graphql.codegen.GetConsignments.{getConsignments => gcs}
import graphql.codegen.GetConsignmentsForMetadataReview.{getConsignmentsForMetadataReview => gcfmr}
import graphql.codegen.GetFileCheckProgress.{getFileCheckProgress => gfcp}
import graphql.codegen.UpdateClientSideDraftMetadataFileName.{updateClientSideDraftMetadataFileName => ucsdmfn}
import graphql.codegen.UpdateConsignmentSeriesId.updateConsignmentSeriesId
import graphql.codegen.types._
import graphql.codegen.{AddConsignment, GetConsignmentFilesMetadata, GetFileCheckProgress}
import services.ApiErrorHandling._
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConsignmentService @Inject() (val graphqlConfiguration: GraphQLConfiguration)(implicit val ec: ExecutionContext) {

  private val getFileCheckProgressClient = graphqlConfiguration.getClient[gfcp.Data, gfcp.Variables]()
  private val getConsignmentClient = graphqlConfiguration.getClient[getConsignment.Data, getConsignment.Variables]()
  private val getConsignmentFilesMetadataClient = graphqlConfiguration.getClient[gcfm.Data, gcfm.Variables]()
  private val addConsignmentClient = graphqlConfiguration.getClient[addConsignment.Data, addConsignment.Variables]()
  private val getConsignmentFileCheckClient = graphqlConfiguration.getClient[gfcp.Data, gfcp.Variables]()
  private val getConsignmentFolderDetailsClient = graphqlConfiguration.getClient[getConsignmentFolderDetails.Data, getConsignmentFolderDetails.Variables]()
  private val getConsignmentSummaryClient = graphqlConfiguration.getClient[getConsignmentSummary.Data, getConsignmentSummary.Variables]()
  private val getConsignmentReferenceClient = graphqlConfiguration.getClient[getConsignmentReference.Data, getConsignmentReference.Variables]()
  private val updateConsignmentSeriesIdClient = graphqlConfiguration.getClient[updateConsignmentSeriesId.Data, updateConsignmentSeriesId.Variables]()
  private val getConsignments = graphqlConfiguration.getClient[gcs.Data, gcs.Variables]()
  private val gctClient = graphqlConfiguration.getClient[gct.Data, gct.Variables]()
  private val getConsignmentsForReviewClient = graphqlConfiguration.getClient[gcfmr.Data, gcfmr.Variables]()
  private val getConsignmentDetailsForReviewClient = graphqlConfiguration.getClient[gcdfmr.Data, gcdfmr.Variables]()
  private val updateDraftMetadataFileNameClient = graphqlConfiguration.getClient[ucsdmfn.Data, ucsdmfn.Variables]()

  def fileCheckProgress(consignmentId: UUID, token: BearerAccessToken): Future[gfcp.GetConsignment] = {
    val variables = gfcp.Variables(consignmentId)
    sendApiRequest(getFileCheckProgressClient, gfcp.document, token, variables).map(data => {
      data.getConsignment match {
        case Some(progress) => progress
        case None           => throw new RuntimeException(s"No data found for file checks for consignment $consignmentId")
      }
    })
  }

  def getConsignmentDetails(consignmentId: UUID, token: BearerAccessToken): Future[getConsignment.GetConsignment] = {
    val variables: getConsignment.Variables = new getConsignment.Variables(consignmentId)

    sendApiRequest(getConsignmentClient, getConsignment.document, token, variables)
      .map(data =>
        data.getConsignment match {
          case Some(consignment) => consignment
          case None              => throw new IllegalStateException(s"No consignment found for consignment $consignmentId")
        }
      )
  }

  def areAllFilesClosed(consignment: GetConsignment): Boolean = {
    !consignment.files
      .filter(file => file.fileMetadata.exists(metadata => metadata.name == fileType && metadata.value == "File"))
      .exists(file => file.fileMetadata.exists(metadata => metadata.name == closureType.name && metadata.value != closureType.value))
  }

  def getConsignmentFileMetadata(
      consignmentId: UUID,
      token: BearerAccessToken,
      metadataType: Option[String],
      fileIds: Option[List[UUID]],
      additionalProperties: Option[List[String]] = None
  ): Future[gcfm.GetConsignment] = {
    val variables: gcfm.Variables =
      new GetConsignmentFilesMetadata.getConsignmentFilesMetadata.Variables(consignmentId, getFileFilters(metadataType, fileIds, additionalProperties))

    sendApiRequest(getConsignmentFilesMetadataClient, gcfm.document, token, variables)
      .map(data =>
        data.getConsignment match {
          case Some(consignment) => consignment
          case None              => throw new IllegalStateException(s"No consignment found for consignment $consignmentId")
        }
      )
  }

  def consignmentExists(consignmentId: UUID, token: BearerAccessToken): Future[Boolean] = {
    val variables: getConsignment.Variables = new getConsignment.Variables(consignmentId)

    sendApiRequest(getConsignmentClient, getConsignment.document, token, variables)
      .map(data => data.getConsignment.isDefined)
  }

  def getConsignmentType(consignmentId: UUID, token: BearerAccessToken): Future[String] = {
    sendApiRequest(gctClient, gct.document, token, gct.Variables(consignmentId))
      .map(data =>
        data.getConsignment
          .flatMap(_.consignmentType)
          .getOrElse(throw new IllegalStateException(s"No consignment type found for consignment $consignmentId"))
      )
  }

  def createConsignment(seriesId: Option[UUID], token: Token): Future[addConsignment.AddConsignment] = {
    val consignmentType: String = if (token.isJudgmentUser) { "judgment" }
    else { "standard" }
    val addConsignmentInput: AddConsignmentInput = AddConsignmentInput(seriesId, consignmentType)
    val variables: addConsignment.Variables = AddConsignment.addConsignment.Variables(addConsignmentInput)

    sendApiRequest(addConsignmentClient, addConsignment.document, token.bearerAccessToken, variables)
      .map(data => data.addConsignment)
  }

  def getConsignmentFileChecks(consignmentId: UUID, token: BearerAccessToken): Future[gfcp.GetConsignment] = {
    val variables: gfcp.Variables = new GetFileCheckProgress.getFileCheckProgress.Variables(consignmentId)

    sendApiRequest(getConsignmentFileCheckClient, gfcp.document, token, variables)
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

  def updateSeriesIdOfConsignment(consignmentId: UUID, seriesId: UUID, token: BearerAccessToken): Future[Boolean] = {
    val updateConsignmentSeriesIdInput = UpdateConsignmentSeriesIdInput(consignmentId, seriesId)
    val variables = new updateConsignmentSeriesId.Variables(updateConsignmentSeriesIdInput)

    sendApiRequest(updateConsignmentSeriesIdClient, updateConsignmentSeriesId.document, token, variables)
      .map(data => data.updateConsignmentSeriesId.isDefined)
  }

  def getConsignments(pageNumber: Int, limit: Int, consignmentFilters: ConsignmentFilters, token: BearerAccessToken): Future[Consignments] = {
    sendApiRequest(getConsignments, gcs.document, token, gcs.Variables(limit, None, Option(pageNumber), Option(consignmentFilters)))
      .map(data => data.consignments)
  }

  def getConsignmentsForReview(token: BearerAccessToken): Future[List[gcfmr.GetConsignmentsForMetadataReview]] = {
    sendApiRequest(getConsignmentsForReviewClient, gcfmr.document, token, gcfmr.Variables())
      .map(data => data.getConsignmentsForMetadataReview)
  }

  def getConsignmentDetailForMetadataReview(consignmentId: UUID, token: BearerAccessToken): Future[gcdfmr.GetConsignment] = {
    val variables = new gcdfmr.Variables(consignmentId)
    sendApiRequest(getConsignmentDetailsForReviewClient, gcdfmr.document, token, variables)
      .map(data => data.getConsignment.get)
  }

  def updateDraftMetadataFileName(consignmentId: UUID, fileName: String, token: BearerAccessToken): Future[Int] = {
    val input = UpdateClientSideDraftMetadataFileNameInput(consignmentId, fileName)
    val variables = new ucsdmfn.Variables(input)
    sendApiRequest(updateDraftMetadataFileNameClient, ucsdmfn.document, token, variables)
      .map(data => data.updateClientSideDraftMetadataFileName.get)
  }

  private def getFileFilters(metadataType: Option[String], fileIds: Option[List[UUID]], additionalProperties: Option[List[String]]): Option[FileFilters] = {
    val metadataTypeFilter = metadataType match {
      case None                => additionalProperties.map(p => FileMetadataFilters(None, None, Some(p)))
      case Some("closure")     => Some(FileMetadataFilters(Some(true), None, additionalProperties))
      case Some("descriptive") => Some(FileMetadataFilters(None, Some(true), additionalProperties))
      case Some(value)         => throw new IllegalArgumentException(s"Invalid metadata type: $value")
    }
    Option(FileFilters(Option("File"), fileIds, None, metadataTypeFilter))
  }
}
object ConsignmentService {

  case class StatusTag(text: String, colour: String)
  case class File(id: UUID, name: String, fileType: Option[String], children: List[File], statusTag: Option[StatusTag] = None)
}
