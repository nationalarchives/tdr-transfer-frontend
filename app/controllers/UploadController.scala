package controllers

import akka.stream.alpakka.s3.MultipartUploadResult
import auth.TokenSecurity
import com.amazonaws.services.s3.transfer.{TransferManager, TransferManagerBuilder}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.types.{AddFileAndMetadataInput, AddFileStatusInput, ConsignmentStatusInput, StartUploadInput}
import io.circe.parser.decode
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms.{mapping, text}
import play.api.i18n.I18nSupport
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{Action, AnyContent, MultipartFormData, Request}
import play.core.parsers.Multipart
import play.core.parsers.Multipart.FileInfo
import services.Statuses.{CompletedValue, InProgressValue, TransferAgreementType, UploadType}
import services._
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UploadController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val frontEndInfoConfiguration: ApplicationConfig,
    val consignmentService: ConsignmentService,
    val uploadService: UploadService,
    val fileStatusService: FileStatusService,
    val backendChecksService: BackendChecksService,
    val awsS3Client: AwsS3Client
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  // permission to be added to ECS task role or execution role for access to upload to s3 bucket
  private val bucketName = "something-else"
  private val s3Client: AmazonS3 = AmazonS3ClientBuilder.defaultClient()

  private val tm: TransferManager = TransferManagerBuilder
    .standard()
    .withS3Client(s3Client)
    .withMultipartUploadThreshold(5 * 1024 * 1025)
    .build()

  private case class FileUploadData(consignmentId: String, fileId: String)

  private val uploadFileForm: Form[FileUploadData] = Form(
    mapping(
      "consignmentId" -> text,
      "fileId" -> text
    )(FileUploadData.apply)(FileUploadData.unapply)
  )

  def triggerBackendChecks(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request =>
    backendChecksService
      .triggerBackendChecks(consignmentId, request.token.bearerAccessToken.getValue)
      .map(res => Ok(res.toString))
  }

  def updateConsignmentStatus(): Action[AnyContent] = secureAction.async { implicit request =>
    request.body.asJson.flatMap(body => {
      decode[ConsignmentStatusInput](body.toString).toOption
    }) match {
      case None        => Future.failed(new Exception(s"Incorrect data provided ${request.body}"))
      case Some(input) => uploadService.updateConsignmentStatus(input, request.token.bearerAccessToken).map(_.toString).map(Ok(_))
    }
  }

  def startUpload(): Action[AnyContent] = secureAction.async { implicit request =>
    request.body.asJson.flatMap(body => {
      decode[StartUploadInput](body.toString).toOption
    }) match {
      case None        => Future.failed(new Exception(s"Incorrect data provided ${request.body}"))
      case Some(input) => uploadService.startUpload(input, request.token.bearerAccessToken).map(Ok(_))
    }
  }

  def saveClientMetadata(): Action[AnyContent] = secureAction.async { implicit request =>
    request.body.asJson.flatMap(body => {
      decode[AddFileAndMetadataInput](body.toString()).toOption
    }) match {
      case Some(metadataInput) => uploadService.saveClientMetadata(metadataInput, request.token.bearerAccessToken).map(res => Ok(res.asJson.noSpaces))
      case None                => Future.failed(new Exception(s"Incorrect data provided ${request.body}"))
    }
  }

  //with alpakka - still needs disk buffering???
  def s3UploadRecords(userId: UUID, consignmentId: UUID, fileId: UUID): Action[MultipartFormData[MultipartUploadResult]] =
    secureAction.async(parse.multipartFormData(handleFilePartAwsUploadResult(userId, consignmentId, fileId))) { implicit request =>
      val profile = request.profiles.head
      val profileUserId = UUID.fromString(profile.getId)
      val token = profile.getAttribute("access_token").asInstanceOf[BearerAccessToken]

      val uploadResult = for {
        details <- consignmentService.getConsignmentDetails(consignmentId, token)
        hasAccess = (userId == profileUserId) && (details.userid == profileUserId)
        result = if (hasAccess) {
          val maybeUploadResult =
            request.body.file("file").map { case FilePart(partName, filename, contentType, multipartUploadResult, count, dispositionType) =>
              multipartUploadResult
            }

          maybeUploadResult.fold(
            InternalServerError("Something went wrong!")
          )(uploadResult => Ok(s"File ${uploadResult.key} upload to bucket ${uploadResult.bucket}"))
        } else {
          Forbidden(s"User does not have access to consignment: $consignmentId")
        }
      } yield result

      uploadResult
    }

  private def handleFilePartAwsUploadResult(userId: UUID, consignmentId: UUID, fileId: UUID): Multipart.FilePartHandler[MultipartUploadResult] = {
    case FileInfo(partName, filename, contentType, dispositionType) =>
      val accumulator = Accumulator(awsS3Client.s3Sink(bucketName, s"${userId.toString}/${consignmentId.toString}/${fileId.toString}"))

      accumulator map { multipartUploadResult =>
        FilePart(partName, filename, contentType, multipartUploadResult)
      }
  }

// without Alpakka
//  def s3UploadRecords(): Action[AnyContent] = secureAction.async { implicit request =>
//    {
//      val uploadData: Form[FileUploadData] = uploadFileForm.bindFromRequest()
//
//      if (uploadData.hasErrors) {
//        Future.failed(new Exception(s"Incorrect data provided $uploadData"))
//      } else {
//        val userId = request.token.userId
//        val data = uploadData.get
//        for {
//          // check user owns consignment here. Cache the result as have to make call each time?
//          details <- consignmentService.getConsignmentDetails(UUID.fromString(data.consignmentId), request.token.bearerAccessToken)
//          hasAccess = details.userid == userId
//          result =
//            if (hasAccess) {
//              val body = request.body.asMultipartFormData.get
//              body.files.map(f => {
//                val putRequest: PutObjectRequest = new PutObjectRequest(bucketName, s"$userId/${data.consignmentId}/${data.fileId}", f.ref)
//                tm.upload(putRequest)
//              })
//              Ok(s"File Uploaded: ${data.fileId}")
//            } else {
//              Forbidden(s"User does not have access to consignment: ${data.consignmentId}")
//            }
//        } yield result
//      }
//    }
//  }

  def addFileStatus(): Action[AnyContent] = secureAction.async { implicit request =>
    request.body.asJson.flatMap(body => {
      decode[AddFileStatusInput](body.toString()).toOption
    }) match {
      case Some(addFileStatusInput) => fileStatusService.addFileStatus(addFileStatusInput, request.token.bearerAccessToken).map(res => Ok(res.asJson.noSpaces))
      case None                     => Future.failed(new Exception(s"Incorrect data provided ${request.body}"))
    }
  }

  def uploadPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield {
      val statusesToValue = consignmentStatusService.getStatusValues(consignmentStatuses, TransferAgreementType, UploadType)
      val transferAgreementStatus: Option[String] = statusesToValue.get(TransferAgreementType).flatten
      val uploadStatus: Option[String] = statusesToValue.get(UploadType).flatten
      val pageHeadingUpload = "Upload your records"
      val pageHeadingUploading = "Uploading your records"

      transferAgreementStatus match {
        case Some(CompletedValue.value) =>
          uploadStatus match {
            case Some(InProgressValue.value) =>
              Ok(views.html.uploadInProgress(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = false))
                .uncache()
            case Some(CompletedValue.value) =>
              Ok(views.html.uploadHasCompleted(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = false))
                .uncache()
            case None =>
              Ok(views.html.standard.upload(consignmentId, reference, pageHeadingUpload, pageHeadingUploading, frontEndInfoConfiguration.frontEndInfo, request.token.name))
                .uncache()
            case _ =>
              throw new IllegalStateException(s"Unexpected Upload status: $uploadStatus for consignment $consignmentId")
          }
        case Some(InProgressValue.value) =>
          Redirect(routes.TransferAgreementPart2Controller.transferAgreement(consignmentId))
        case None =>
          Redirect(routes.TransferAgreementPart1Controller.transferAgreement(consignmentId))
        case _ =>
          throw new IllegalStateException(s"Unexpected Transfer Agreement status: $transferAgreementStatus for consignment $consignmentId")
      }
    }
  }

  def judgmentUploadPage(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield {
      val uploadStatus: Option[String] = consignmentStatusService.getStatusValues(consignmentStatuses, UploadType).values.headOption.flatten
      val pageHeadingUpload = "Upload judgment"
      val pageHeadingUploading = "Uploading judgment"

      uploadStatus match {
        case Some(InProgressValue.value) =>
          Ok(views.html.uploadInProgress(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = true))
            .uncache()
        case Some(CompletedValue.value) =>
          Ok(views.html.uploadHasCompleted(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = true))
            .uncache()
        case None =>
          Ok(views.html.judgment.judgmentUpload(consignmentId, reference, pageHeadingUpload, pageHeadingUploading, frontEndInfoConfiguration.frontEndInfo, request.token.name))
            .uncache()
        case _ =>
          throw new IllegalStateException(s"Unexpected Upload status: $uploadStatus for consignment $consignmentId")
      }
    }
  }
}
