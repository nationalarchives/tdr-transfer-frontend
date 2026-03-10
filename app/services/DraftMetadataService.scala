package services

import com.google.inject.Inject
import configuration.ApplicationConfig
import io.circe.Decoder
import io.circe.generic.auto._
import io.circe.parser.decode
import play.api.{Configuration, Logging}
import services.StepFunction.StepFunctionInput
import software.amazon.awssdk.core.ResponseBytes
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.validation.Metadata

import java.nio.charset.StandardCharsets
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

object FileError extends Enumeration {
  type FileError = Value
  val UTF_8, INVALID_CSV, ROW_VALIDATION, DUPLICATE_HEADER, ADDITIONAL_HEADER, SCHEMA_REQUIRED, SCHEMA_VALIDATION, VIRUS, UNKNOWN, NONE = Value
}

case class Error(validationProcess: String, property: String, errorKey: String, message: String)
case class ValidationErrors(assetId: String, errors: Set[Error], data: List[Metadata] = List.empty[Metadata])
case class ErrorFileData(consignmentId: UUID, date: String, fileError: FileError.FileError, validationErrors: List[ValidationErrors])

class DraftMetadataService @Inject() (val stepFunction: StepFunction, val configuration: Configuration, val applicationConfig: ApplicationConfig, val downloadService: DownloadService)(
    implicit val executionContext: ExecutionContext
) extends Logging {

  implicit val FileErrorDecoder: Decoder[FileError.Value] = Decoder.decodeEnumeration(FileError)

  def triggerDraftMetadataValidator(consignmentId: UUID, uploadFileName: String, token: Token): Future[Boolean] = {
    logger.info(s"Draft metadata validator was triggered by ${token.userId} for consignment:$consignmentId")
    val stepFunctionArn = s"${configuration.get[String]("metadatavalidation.stepFunctionArn")}"
    val stepFunctionName = "Metadata Validation"
    val input = DraftMetadataStepFunctionInput(consignmentId.toString, uploadFileName)
    stepFunction.triggerStepFunction(stepFunctionArn, input, stepFunctionName, consignmentId)
  }

  def getErrorTypeFromErrorJson(consignmentId: UUID): Future[FileError.FileError] = {
    getErrorReport(consignmentId).map(_.fileError)
  }

  def getErrorReport(consignmentId: UUID): Future[ErrorFileData] = {
    val errorFile: Future[ResponseBytes[GetObjectResponse]] =
      downloadService.downloadFile(applicationConfig.draft_metadata_s3_bucket_name, s"$consignmentId/${applicationConfig.draftMetadataErrorFileName}")
    val unknownError = ErrorFileData(consignmentId, date = "", FileError.UNKNOWN, validationErrors = Nil)
    errorFile
      .map(responseBytes => {
        val errorJson = new String(responseBytes.asByteArray(), StandardCharsets.UTF_8)
        decode[ErrorFileData](errorJson).getOrElse(unknownError)
      })
      .recoverWith(_ => Future.successful(unknownError))
  }
}

case class DraftMetadataStepFunctionInput(consignmentId: String, fileName: String) extends StepFunctionInput
