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
import services.Statuses._
import services._
import uk.gov.nationalarchives.tdr.keycloak.Token
import viewsapi.Caching.preventCaching

import java.io.{BufferedInputStream, FileInputStream}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

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
    for {
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield {
      Ok(views.html.draftmetadata.draftMetadataUpload(consignmentId, reference, frontEndInfoConfiguration.frontEndInfo, request.token.bearerAccessToken.getValue))
        .uncache()
    }
  }

  def saveDraftMetadata(consignmentId: java.util.UUID): Action[MultipartFormData[TemporaryFile]] = secureAction.async(parse.multipartFormData) {

    implicit request: Request[MultipartFormData[TemporaryFile]] =>
      val token = request.asInstanceOf[Request[AnyContent]].token

      val checkIfUploadIsInProgress = isDraftMetadataUploadIsInProgress(consignmentId, token)
      checkIfUploadIsInProgress.flatMap {
        case true  => Future.successful(Redirect(routes.DraftMetadataChecksController.draftMetadataChecksPage(consignmentId)))
        case false =>
          uploadDraftMetadata(consignmentId, token)
            .recoverWith { case error =>
              val errorPage = for {
                reference <- consignmentService.getConsignmentRef(consignmentId, token.bearerAccessToken)
                consignmentStatusInput = ConsignmentStatusInput(consignmentId, DraftMetadataUploadType.id, Some(CompletedWithIssuesValue.value), None)
                _ <- consignmentStatusService.updateConsignmentStatus(consignmentStatusInput, token.bearerAccessToken)
              } yield {
                logger.error(error.getMessage, error)
                Ok(views.html.draftmetadata.draftMetadataUploadError(consignmentId, reference, frontEndInfoConfiguration.frontEndInfo, token.bearerAccessToken.getValue)).uncache()
              }
              IO.fromFuture(IO(errorPage))
            }
            .unsafeToFuture()
      }
  }

  private def isDraftMetadataUploadIsInProgress(consignmentId: UUID, token: Token): Future[Boolean] = {
    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token.bearerAccessToken)
      draftMetadataStatus = consignmentStatusService.getStatusValues(consignmentStatuses, DraftMetadataUploadType).values.headOption.flatten
      page <- draftMetadataStatus match {
        case Some(status) if status == InProgressValue.value => Future.successful(true)
        case Some(_)                                         => Future.successful(false)
        case None                                            =>
          consignmentStatusService
            .addConsignmentStatus(consignmentId, DraftMetadataUploadType.id, InProgressValue.value, token.bearerAccessToken)
            .map(_ => false)
      }
    } yield page
  }

  private def uploadDraftMetadata(consignmentId: UUID, token: Token)(implicit request: Request[MultipartFormData[TemporaryFile]]): IO[Result] = {
    val uploadBucket = s"${applicationConfig.draft_metadata_s3_bucket_name}"
    val uploadFileName = applicationConfig.draftMetadataFileName
    val uploadKey = s"$consignmentId/$uploadFileName"
    val noDraftMetadataFileUploaded: String = "No meta data file provided"
    val consignmentStatusInput = ConsignmentStatusInput(consignmentId, DraftMetadataType.id, Some(InProgressValue.value), None)

    for {
      _ <- IO(logger.info(s"User:${token.userId} uploaded the draft metadata file for consignment:$consignmentId"))
      _ <- IO.fromFuture(IO(consignmentStatusService.updateConsignmentStatus(consignmentStatusInput, token.bearerAccessToken)))
      firstFilePart <- fromOption(request.body.files.headOption)(new RuntimeException(noDraftMetadataFileUploaded))
      file <- fromOption(request.body.file(firstFilePart.key))(new RuntimeException(noDraftMetadataFileUploaded))
      draftMetadataBytes = new BufferedInputStream(new FileInputStream(file.ref.getAbsoluteFile)).readAllBytes()
      _ <- IO.fromFuture(IO(uploadService.uploadDraftMetadata(uploadBucket, uploadKey, draftMetadataBytes)))
      _ <- IO.fromFuture(IO { consignmentService.updateDraftMetadataFileName(consignmentId, file.filename, token.bearerAccessToken) })
      _ <- IO.fromFuture(IO { draftMetadataService.triggerDraftMetadataValidator(consignmentId, uploadFileName, token) })
      uploadConsignmentStatus = ConsignmentStatusInput(consignmentId, DraftMetadataUploadType.id, Some(CompletedValue.value), None)
      _ <- IO.fromFuture(IO(consignmentStatusService.updateConsignmentStatus(uploadConsignmentStatus, token.bearerAccessToken)))
      successPage <- IO(Redirect(routes.DraftMetadataChecksController.draftMetadataChecksPage(consignmentId)))
    } yield successPage
  }
}
