package controllers

import auth.TokenSecurity
import com.amazonaws.services.s3.transfer.{TransferManager, TransferManagerBuilder}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent}
import services.{BackendChecksService, ConsignmentService, FileStatusService, UploadService}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UploadControllerV2 @Inject() (val controllerComponents: SecurityComponents,
                         val graphqlConfiguration: GraphQLConfiguration,
                         val keycloakConfiguration: KeycloakConfiguration,
                         val frontEndInfoConfiguration: ApplicationConfig,
                         val consignmentService: ConsignmentService,
                         val uploadService: UploadService,
                         val fileStatusService: FileStatusService,
                         val backendChecksService: BackendChecksService)(implicit val ec: ExecutionContext) extends TokenSecurity
  with I18nSupport {

  //permission to be added to ECS task role or execution role for access to upload to s3 bucket
  val bucketName = "something-else"
  val s3Client: AmazonS3 = AmazonS3ClientBuilder.defaultClient()

  val tm: TransferManager = TransferManagerBuilder.standard()
    .withS3Client(s3Client)
    .withMultipartUploadThreshold(5 * 1024 * 1025)
    .build()

  case class FileUploadData(consignmentId: String, fileId: String)

  private val uploadFileForm: Form[FileUploadData] = Form(
    mapping(
      "consignmentId" -> text,
      "fileId" -> text
    )
    (FileUploadData.apply)(FileUploadData.unapply)
  )

  def s3UploadRecords(): Action[AnyContent] =  secureAction.async { implicit request => {
      val uploadData: Form[FileUploadData] = uploadFileForm.bindFromRequest()

      if (uploadData.hasErrors) {
        Future.failed(new Exception(s"Incorrect data provided $uploadData"))
      } else {
        val userId = request.token.userId
        val data = uploadData.get
        for {
          //check user owns consignment here. Cache the result as have to make call each time
          details <- consignmentService.getConsignmentDetails(UUID.fromString(data.consignmentId), request.token.bearerAccessToken)
          hasAccess = details.userid == userId
          result = if (hasAccess) {
            val body = request.body.asMultipartFormData.get
            body.files.map(f => {
              tm.upload(bucketName, s"$userId/${data.consignmentId}/${data.fileId}", f.ref)
            })
            Ok(s"File Uploaded: ${data.fileId}")
          } else {
            Forbidden(
              views.html.forbiddenError(
                request.token.name,
                getProfile(request).isPresent,
                request.token.isJudgmentUser
              )(request2Messages(request), request)
            )}
        } yield result

//        val body = request.body.asMultipartFormData.get
//
//        body.files.map(f => {
//          tm.upload(bucketName, s"${data.userId}/${data.consignmentId}/${data.fileId}", f.ref)
//        })
//
//        Future(Ok(s"Files Uploaded"))
      }
    }
  }

//  type FilePartHandler[A] = FileInfo => Accumulator[ByteString, FilePart[A]]
//
//  def handleFilePartAsFile: FilePartHandler[File] = {
//    case FileInfo(partName, filename, contentType, dispositionType) =>
//      val perms = java.util.EnumSet.of(OWNER_READ, OWNER_WRITE)
//      val attr = PosixFilePermissions.asFileAttribute(perms)
//      val path = JFiles.createTempFile("multipartBody", "tempFile", attr)
//      val file = path.toFile
//      val fileSink = FileIO.toPath(path)
//      val accumulator = Accumulator(fileSink)
//      accumulator.map {
//        case IOResult(count, status) =>
//          FilePart(partName, filename, contentType, file, count, dispositionType)
//      }(ec)
//  }
//
//
//  def startUpload(consignmentId: UUID) = secureAction(parse.multipartFormData(handleFilePartAsFile)) { request =>
//    request.body.files.map { file => {
//        tm.upload("something-else", s"${consignmentId.toString}/${file.filename}", file.ref)
//      }
//    }
//    Ok(s"Files Uploaded")
//  }
}
