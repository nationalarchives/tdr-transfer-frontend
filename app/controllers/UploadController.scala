package controllers

import auth.TokenSecurity
import configuration.{FrontEndInfoConfiguration, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.types.{AddFileAndMetadataInput, AddFileStatusInput, ClientSideMetadataInput, ConsignmentStatusInput, StartUploadInput}
import io.circe.parser.decode
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.GoogleDriveService.DriveTransferEvent
import services.{ConsignmentService, ConsignmentStatusService, FileStatusService, GoogleDriveService, UploadService}
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UploadController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val frontEndInfoConfiguration: FrontEndInfoConfiguration,
    val consignmentService: ConsignmentService,
    val uploadService: UploadService,
    val fileStatusService: FileStatusService,
    val googleDriveService: GoogleDriveService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

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

  def addFileStatus(): Action[AnyContent] = secureAction.async { implicit request =>
    request.body.asJson.flatMap(body => {
      decode[AddFileStatusInput](body.toString()).toOption
    }) match {
      case Some(addFileStatusInput) => fileStatusService.addFileStatus(addFileStatusInput, request.token.bearerAccessToken).map(res => Ok(res.asJson.noSpaces))
      case None                     => Future.failed(new Exception(s"Incorrect data provided ${request.body}"))
    }
  }

  def driveSubmit(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      token <- googleDriveService.getGoogleToken(request.token.bearerAccessToken.getValue)
      parentFolder = request.body.asFormUrlEncoded.getOrElse(Map()).filter(m => m._1 != "csrfToken" && !m._2.contains("false")).head
      fileMetadata <- googleDriveService.getFileMetadata(token, parentFolder._1)
      _ <- uploadService.startUpload(StartUploadInput(consignmentId, parentFolder._1), request.token.bearerAccessToken)
      clientMetadataInput = AddFileAndMetadataInput(
        consignmentId,
        fileMetadata.zipWithIndex.map { case (m, i) =>
          ClientSideMetadataInput(m.path, m.hash, 1, m.size, i)

        },
        None
      )
      grouped = clientMetadataInput.metadataInput.groupBy(_.matchId).map { case (m, i) => (m, i.head) }
      clientMetadata <- uploadService.saveClientMetadata(clientMetadataInput, request.token.bearerAccessToken)
      transferEvent = clientMetadata.map(i =>
        DriveTransferEvent(
          token,
          "tdr-upload-files-cloudfront-dirty-intg",
          s"/${parentFolder._1}",
          grouped(i.matchId).originalPath,
          s"${request.token.userId}/$consignmentId/${i.fileId}"
        )
      )
      _ <- Future.sequence(transferEvent.map(e => googleDriveService.invokeTransferLambda(e)))
    } yield Redirect(routes.FileChecksController.fileChecksPage(consignmentId))
  }

  def drivePage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignmentReference <- Future("REF")//consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      googleToken <- googleDriveService.getGoogleToken(request.token.bearerAccessToken.getValue)
    } yield {
      val files = googleDriveService
        .getFiles(googleToken)
        .filter(_.getMimeType == "application/vnd.google-apps.folder")
      Ok(views.html.drive(consignmentId, consignmentReference, request.token.name, files))
    }
  }

  def uploadPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)
    if (true) {
      val accountUrl = googleDriveService.getAccountUrl(request.profile, s"consignment/$consignmentId/drive-upload").getOrElse(throw new Exception("bob"))
      Future(Redirect(accountUrl))
    } else {
      for {
        consignmentStatus <- consignmentStatusService.getConsignmentStatus(consignmentId, request.token.bearerAccessToken)
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      } yield {
        val transferAgreementStatus: Option[String] = consignmentStatus.flatMap(_.transferAgreement)
        val uploadStatus: Option[String] = consignmentStatus.flatMap(_.upload)
        val pageHeadingUpload = "Upload your records"
        val pageHeadingUploading = "Uploading records"

        transferAgreementStatus match {
          case Some("Completed") =>
            uploadStatus match {
              case Some("InProgress") =>
                Ok(views.html.uploadInProgress(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = false))
                  .uncache()
              case Some("Completed") =>
                Ok(views.html.uploadHasCompleted(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = false))
                  .uncache()
              case None =>
                Ok(views.html.standard.upload(consignmentId, reference, pageHeadingUpload, pageHeadingUploading, frontEndInfoConfiguration.frontEndInfo, request.token.name))
                  .uncache()
              case _ =>
                throw new IllegalStateException(s"Unexpected Upload status: $uploadStatus for consignment $consignmentId")
            }
          case Some("InProgress") =>
            Redirect(routes.TransferAgreementComplianceController.transferAgreement(consignmentId))
          case None =>
            Redirect(routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId))
          case _ =>
            throw new IllegalStateException(s"Unexpected Transfer Agreement status: $transferAgreementStatus for consignment $consignmentId")
        }
      }
    }

  }

  def judgmentUploadPage(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

    for {
      consignmentStatus <- consignmentStatusService.getConsignmentStatus(consignmentId, request.token.bearerAccessToken)
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield {
      val uploadStatus: Option[String] = consignmentStatus.flatMap(_.upload)
      val pageHeadingUpload = "Upload judgment"
      val pageHeadingUploading = "Uploading judgment"

      uploadStatus match {
        case Some("InProgress") =>
          Ok(views.html.uploadInProgress(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = true))
            .uncache()
        case Some("Completed") =>
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
