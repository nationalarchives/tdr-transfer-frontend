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
        Ok(views.html.draftmetadata.draftMetadataUpload(consignmentId, reference, frontEndInfoConfiguration.frontEndInfo, request.token.name))
          .uncache()
      }
    }
  }

  def saveDraftMetadata(consignmentId: java.util.UUID): Action[MultipartFormData[TemporaryFile]] = secureAction.async(parse.multipartFormData) {

    implicit request: Request[MultipartFormData[TemporaryFile]] =>
      val successPage = routes.DraftMetadataChecksController.draftMetadataChecksPage(consignmentId)
      val userId = request.asInstanceOf[Request[AnyContent]].token.userId

      val uploadBucket = s"tdr-draft-metadata-${applicationConfig.frontEndInfo.stage}"
      val uploadKey = s"$userId/$consignmentId/draft-metadata.csv"

      def uploadMetaData: IO[Result] = for {
        firstFilePart <- fromOption(request.body.files.headOption)(new RuntimeException("No meta data file provided "))
        file <- fromOption(request.body.file(firstFilePart.key))(new RuntimeException("No meta data file provided"))
        draftMetadata <- fromOption(Using(scala.io.Source.fromFile(file.ref.getAbsoluteFile))(_.mkString).toOption)(new RuntimeException("No meta data file provided"))
        _ <- uploadService.uploadDraftMetadata(uploadBucket, uploadKey, draftMetadata)
        successPage <- IO(play.api.mvc.Results.Redirect(successPage))
      } yield successPage

      uploadMetaData
        .recoverWith { case error =>
          IO(InternalServerError("Unable to upload draft metadata: " + error.getMessage))
        }
        .unsafeToFuture()

  }

}
