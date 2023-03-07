package controllers

import auth.TokenSecurity
import com.typesafe.config.{Config, ConfigFactory}
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.types.{AddFileAndMetadataInput, AddFileStatusInput, ConsignmentStatusInput, StartUploadInput}
import io.circe.parser.decode
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses.{CompletedValue, InProgressValue, TransferAgreementType, UploadType}
import services.{BackendChecksService, ConsignmentService, ConsignmentStatusService, FileStatusService, UploadService}
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
    val backendChecksService: BackendChecksService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

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
