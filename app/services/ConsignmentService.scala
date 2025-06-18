package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import graphql.codegen.types._
import services.DynamoService.{AddConsignment, GetConsignment}
import services.S3Service.{FileChecks, GetConsignmentFileMetadata}
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import software.amazon.awssdk.services.sns.model.ResourceNotFoundException
import uk.gov.nationalarchives.tdr.keycloak.Token

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ConsignmentService @Inject() (dynamoService: DynamoService, s3Service: S3Service)(implicit val ec: ExecutionContext) {

  def getConsignmentDetails(consignmentId: UUID, token: BearerAccessToken): Future[GetConsignment] = {
    dynamoService.getConsignment(consignmentId)
  }

  def updateIndividualCustomMetadata(userId: UUID, consignmentId: UUID, csvFile: Array[Byte]): Future[List[PutObjectResponse]] =
    s3Service.saveCustomMetadata(userId, consignmentId, csvFile)

  def setUploadStatus(consignmentId: UUID, status: String, fileCount: Int): Future[Unit] = for {
    _ <- dynamoService.setFieldToValue(consignmentId, "status_Upload", status)
    _ <- dynamoService.setFieldToValue(consignmentId, "fileCount", fileCount)
  } yield ()

  def getConsignmentFileMetadata(
      consignmentId: UUID,
      token: Token,
      metadataType: Option[String],
      fileIds: Option[List[UUID]],
      additionalProperties: Option[List[String]] = None
  ): Future[GetConsignmentFileMetadata] = {
    val userId = token.userId
    for {
      files <- s3Service.getConsignmentFileMetadata(userId, consignmentId)
      consignment <- dynamoService.getConsignment(consignmentId)
    } yield GetConsignmentFileMetadata(files, consignment.consignmentReference)
  }

  def consignmentExists(consignmentId: UUID, token: BearerAccessToken): Future[Boolean] = {
    dynamoService.getConsignment(consignmentId).recoverWith {
      case _: ResourceNotFoundException => Future.successful(false)
      case e => throw e
    }.map(_ => true)

  }

  def getConsignmentType(consignmentId: UUID): Future[String] = {
    dynamoService.getConsignment(consignmentId).map(_.consignmentType)
  }

  def createConsignment(seriesId: Option[UUID], token: Token): Future[AddConsignment] = {
    val consignmentType: String = if (token.isJudgmentUser) { "judgment" }
    else { "standard" }
    dynamoService.createConsignment(token.userId, token.transferringBody.getOrElse(""), seriesId, consignmentType)
  }

  def getConsignmentFileChecks(consignmentId: UUID, token: Token): Future[FileChecks] = {
    s3Service.getFileChecksStatus(token.userId, consignmentId)
  }

  def getConsignmentConfirmTransfer(consignmentId: UUID): Future[GetConsignment] = {
    dynamoService.getConsignment(consignmentId)
  }

  def getConsignmentRef(consignmentId: UUID): Future[String] = {
    dynamoService.getConsignment(consignmentId).map(_.consignmentReference)
  }

  def updateSeriesIdOfConsignment(consignmentId: UUID, seriesId: UUID, token: BearerAccessToken): Future[Boolean] =  for {
    _ <- dynamoService.setFieldToValue(consignmentId, "series", seriesId).map(_.sdkHttpResponse().isSuccessful)
    _ <- dynamoService.setFieldToValue(consignmentId, "status_Series", "Completed").map(_.sdkHttpResponse().isSuccessful)
  } yield true

  def getConsignments(pageNumber: Int, limit: Int, consignmentFilters: ConsignmentFilters, token: BearerAccessToken): Future[List[GetConsignment]] = {
    val userId = consignmentFilters.userId.get
    dynamoService.getConsignments(userId.toString, "userId")
  }

  def getConsignmentsForReview(token: BearerAccessToken): Future[List[GetConsignment]] = {
    dynamoService.getConsignments("true", "needsReview")
  }

  def getConsignmentDetailForMetadataReview(consignmentId: UUID, token: BearerAccessToken): Future[List[GetConsignment]] = {
    getConsignmentsForReview(token)
  }

  def updateDraftMetadataFileName(consignmentId: UUID, fileName: String, token: BearerAccessToken): Future[Int] = {
    dynamoService.setFieldToValue(consignmentId, "clientSideDraftMetadataFileName", fileName).map(_.sdkHttpResponse().statusCode())
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
