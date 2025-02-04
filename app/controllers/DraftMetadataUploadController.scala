package controllers

import auth.TokenSecurity
import cats.effect.IO
import cats.effect.IO.fromOption
import cats.effect.unsafe.implicits.global
import configuration.{ApplicationConfig, KeycloakConfiguration}
import graphql.codegen.types.ConsignmentStatusInput
import org.pac4j.play.scala.SecurityComponents
import play.api._
import play.api.i18n.I18nSupport
import play.api.libs.Files.TemporaryFile
import play.api.mvc._
import services.Statuses.{DraftMetadataType, InProgressValue}
import services._
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

@Singleton
class DraftMetadataUploadController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val frontEndInfoConfiguration: ApplicationConfig,
    val consignmentService: ConsignmentService,
    val uploadService: UploadService,
    val draftMetadataService: DraftMetadataService,
    val consignmentStatusService: ConsignmentStatusService,
    val applicationConfig: ApplicationConfig
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport
    with Logging {

  def draftMetadataUploadPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    if (applicationConfig.blockDraftMetadataUpload) {
      Future(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
    } else {
      for {
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      } yield {
        Ok(views.html.draftmetadata.draftMetadataUpload(consignmentId, reference, frontEndInfoConfiguration.frontEndInfo, request.token.bearerAccessToken.getValue))
          .uncache()
      }
    }
  }

  def saveDraftMetadata(consignmentId: java.util.UUID): Action[MultipartFormData[TemporaryFile]] = secureAction.async(parse.multipartFormData) {

    implicit request: Request[MultipartFormData[TemporaryFile]] =>
      val successPage = routes.DraftMetadataChecksController.draftMetadataChecksPage(consignmentId)
      val token = request.asInstanceOf[Request[AnyContent]].token
      val uploadBucket = s"${applicationConfig.draft_metadata_s3_bucket_name}"
      val uploadFileName = applicationConfig.draftMetadataFileName
      val userUploadedFile = request.body.files.headOption
      val uploadKey = s"$consignmentId/$uploadFileName"
      val noDraftMetadataFileUploaded: String = "No meta data file provided"
      val consignmentStatusInput = ConsignmentStatusInput(consignmentId, DraftMetadataType.id, Some(InProgressValue.value))

      def uploadDraftMetadata: IO[Result] = for {
        _ <- IO(logger.info(s"User:${token.userId} uploaded the draft metadata file '${userUploadedFile.map(_.filename).getOrElse(uploadFileName)}' for consignment:$consignmentId"))
        _ <- IO.fromFuture(IO(consignmentStatusService.updateConsignmentStatus(consignmentStatusInput, token.bearerAccessToken)))
        firstFilePart <- fromOption(userUploadedFile)(new RuntimeException(noDraftMetadataFileUploaded))
        file <- fromOption(request.body.file(firstFilePart.key))(new RuntimeException(noDraftMetadataFileUploaded))
        draftMetadata <- fromOption(Using(scala.io.Source.fromFile(file.ref.getAbsoluteFile))(_.mkString).toOption)(new RuntimeException(noDraftMetadataFileUploaded))
        _ <- IO.fromFuture(IO(uploadService.uploadDraftMetadata(uploadBucket, uploadKey, draftMetadata)))
        _ <- IO.fromFuture(IO { draftMetadataService.triggerDraftMetadataValidator(consignmentId, uploadFileName, token) })
        successPage <- IO(play.api.mvc.Results.Redirect(successPage))
      } yield successPage

      uploadDraftMetadata
        .recoverWith { case error =>
          val errorPage = for {
            reference <- consignmentService.getConsignmentRef(consignmentId, token.bearerAccessToken)
          } yield {
            logger.error(error.getMessage, error)
            Ok(views.html.draftmetadata.draftMetadataUploadError(consignmentId, reference, frontEndInfoConfiguration.frontEndInfo, token.bearerAccessToken.getValue)).uncache()
          }
          IO.fromFuture(IO(errorPage))
        }
        .unsafeToFuture()
  }
}
