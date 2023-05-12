package controllers

import akka.stream.IOResult
import akka.stream.scaladsl._
import akka.util.ByteString
import auth.TokenSecurity
import com.amazonaws.auth.profile.ProfileCredentialsProvider
import com.amazonaws.auth.STSSessionCredentialsProvider
import com.amazonaws.auth.{AWSStaticCredentialsProvider, BasicSessionCredentials, STSSessionCredentialsProvider, WebIdentityTokenCredentialsProvider}
import com.amazonaws.regions.Regions
import com.amazonaws.services.s3.transfer.{TransferManager, TransferManagerBuilder}
import com.amazonaws.services.s3.{AmazonS3, AmazonS3ClientBuilder}
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.libs.streams.Accumulator
import play.api.mvc.MultipartFormData.FilePart
import play.api.mvc.{Action, AnyContent, Request}
import play.core.parsers.Multipart.FileInfo
import services.{BackendChecksService, ConsignmentService, FileStatusService, UploadService}
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder
import com.amazonaws.services.securitytoken.model.{AssumeRoleRequest, Credentials}

import java.io.File
import java.nio.file.attribute.PosixFilePermission._
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.{Paths, Files => JFiles}
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
  val s3Client: AmazonS3 = AmazonS3ClientBuilder.defaultClient()

  val tm: TransferManager = TransferManagerBuilder.standard()
    .withS3Client(s3Client)
    .withMultipartUploadThreshold(5 * 1024 * 1025)
    .build()

  def uploadPage(consignmentId: UUID): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] => {
      Ok(views.html.standard.uploadV2(consignmentId, frontEndInfoConfiguration.frontEndInfo, "UploadV2"))
    }
  }

  val consignmentId = parse.using { request =>

    request.session
      .get("username")
      .map { user =>
        parse.file(to = new File("/tmp/" + user + ".upload"))
      }
      .getOrElse {
        sys.error("You don't have the right to upload here")
      }
  }

  def save = Action(consignmentId) { request =>
    Ok("Saved the request content to " + request.body)
  }

  def s3UploadRecords() = secureAction.async(parse.temporaryFile) { implicit request => {

    val id = request.profiles.head.getId
    val tempFile = request.body
    tm.upload("something-else", s"$id", tempFile)

//    val a = request.body.files.map(file => {
//      tm.upload("something-else", s"${request.profiles.head.getId}/${file.filename}", file.ref)
//    })
    println("HERE")
    Future(Ok(s"Files Uploaded"))
  }
  }

  type FilePartHandler[A] = FileInfo => Accumulator[ByteString, FilePart[A]]

  def handleFilePartAsFile: FilePartHandler[File] = {
    case FileInfo(partName, filename, contentType, dispositionType) =>
      val perms = java.util.EnumSet.of(OWNER_READ, OWNER_WRITE)
      val attr = PosixFilePermissions.asFileAttribute(perms)
      val path = JFiles.createTempFile("multipartBody", "tempFile", attr)
      val file = path.toFile
      val fileSink = FileIO.toPath(path)
      val accumulator = Accumulator(fileSink)
      accumulator.map {
        case IOResult(count, status) =>
          FilePart(partName, filename, contentType, file, count, dispositionType)
      }(ec)
  }

//  def startUpload(consignmentId: UUID) = secureAction(parse.multipartFormData) { request =>
//    request.body.files.map { file => {
//        tm.upload("something-else", s"${consignmentId.toString}/${file.filename}", file.ref)
//      }
//    }
//    Ok(s"Files Uploaded")
//  }


  def startUpload(consignmentId: UUID) = secureAction(parse.multipartFormData(handleFilePartAsFile)) { request =>
    request.body.files.map { file => {
        tm.upload("something-else", s"${consignmentId.toString}/${file.filename}", file.ref)
      }
    }
    Ok(s"Files Uploaded")
  }
}
