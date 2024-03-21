package controllers

import auth.TokenSecurity
import cats.effect.IO
import cats.effect.IO.fromOption
import cats.effect.unsafe.implicits.global
import configuration.{ApplicationConfig, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.libs.Files.TemporaryFile
import play.api.mvc.{Action, AnyContent, MultipartFormData, Request, Result}
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
    val applicationConfig: ApplicationConfig
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def draftMetadataUploadPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
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
      val uploadKey = s"$consignmentId/draft-metadata.csv"
      val noDraftMetadataFileUploaded: String = "No meta data file provided"

      def uploadDraftMetadata: IO[Result] = for {
        firstFilePart <- fromOption(request.body.files.headOption)(new RuntimeException(noDraftMetadataFileUploaded))
        file <- fromOption(request.body.file(firstFilePart.key))(new RuntimeException(noDraftMetadataFileUploaded))
        draftMetadata <- fromOption(Using(scala.io.Source.fromFile(file.ref.getAbsoluteFile))(_.mkString).toOption)(new RuntimeException(noDraftMetadataFileUploaded))
        _ <- uploadService.uploadDraftMetadata(uploadBucket, uploadKey, draftMetadata)
        _ <- IO.fromFuture(IO { draftMetadataService.triggerDraftMetadataValidator(consignmentId, token.bearerAccessToken.getValue) })
        successPage <- IO(play.api.mvc.Results.Redirect(successPage))
      } yield successPage

      uploadDraftMetadata
        .recoverWith { case error =>
          IO(InternalServerError(s"Unable to upload draft metadata to : $uploadBucket/$uploadKey: Error:" + error.getMessage + " stack" + error.getStackTrace.mkString))
        }
        .unsafeToFuture()
  }
}
