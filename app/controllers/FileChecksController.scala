package controllers

import java.util.UUID
import auth.TokenSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.types.ConsignmentStatusInput
import io.circe.syntax._
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import org.apache.commons.lang3.BooleanUtils.{TRUE}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import services.Statuses.{CompletedValue, CompletedWithIssuesValue, UploadType}
import services.{BackendChecksService, ConsignmentService, ConsignmentStatusService}
import viewsapi.Caching.preventCaching

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileChecksController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val applicationConfig: ApplicationConfig,
    val backendChecksService: BackendChecksService,
    val consignmentStatusService: ConsignmentStatusService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  private def getFileChecksProgress(request: Request[AnyContent], consignmentId: UUID)(implicit requestHeader: RequestHeader): Future[FileChecksProgress] = {
    consignmentService
      .getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken)
      .map { fileCheckProgress =>
        FileChecksProgress(
          fileCheckProgress.totalFiles,
          fileCheckProgress.fileChecks.antivirusProgress.filesProcessed * 100 / fileCheckProgress.totalFiles,
          fileCheckProgress.fileChecks.checksumProgress.filesProcessed * 100 / fileCheckProgress.totalFiles,
          fileCheckProgress.fileChecks.ffidProgress.filesProcessed * 100 / fileCheckProgress.totalFiles
        )
      }
  }

  private def checkUploadStatus(consignmentId: UUID, uploadFailed: Option[String], token: BearerAccessToken) = {
    if (uploadFailed.contains("true")) {
      for {
        _ <- consignmentStatusService.updateConsignmentStatus(ConsignmentStatusInput(consignmentId, UploadType.id, Some(CompletedWithIssuesValue.value)), token)
      } yield {
        throw new Exception(s"Upload failed for consignment $consignmentId")
      }
    }
  }

  private def handleFailedUpload(consignmentId: UUID, token: BearerAccessToken)(implicit request: Request[AnyContent]) = {
    for {
      _ <- consignmentStatusService.updateConsignmentStatus(ConsignmentStatusInput(consignmentId, UploadType.id, Some(CompletedWithIssuesValue.value)), token)
    } yield {
      Ok(views.html.uploadInProgress(consignmentId, "reference", "Uploading your records", request.token.name, isJudgmentUser = true))
        .uncache()
    }
  }

  def backendChecks(consignmentId: UUID, token: BearerAccessToken): Future[Boolean] = {
    for {
      statuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
      uploadStatus = statuses.find(_.statusType == UploadType.id)
      backendChecksTriggered <- if (uploadStatus.isDefined && uploadStatus.get.value != CompletedWithIssuesValue.value) {
        backendChecksService.triggerBackendChecks(consignmentId, token.getValue)
      } else {
        Future(throw new IllegalStateException(s"Unexpected Upload state: for consignment $consignmentId"))
      }
      uploadStatusUpdate = if (backendChecksTriggered) {
        CompletedValue
      } else {
        CompletedWithIssuesValue
      }
      _ <- consignmentStatusService.updateConsignmentStatus(ConsignmentStatusInput(consignmentId, UploadType.id, Some(uploadStatusUpdate.value)), token)
    } yield backendChecksTriggered
  }

  def fileCheckProgress(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request =>
    consignmentService
      .fileCheckProgress(consignmentId, request.token.bearerAccessToken)
      .map(_.asJson.noSpaces)
      .map(Ok(_))
  }

  def fileChecksPage(consignmentId: UUID, uploadFailed: Option[String]): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val token = request.token.bearerAccessToken
    checkUploadStatus(consignmentId, uploadFailed, token)

    for {
      statuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
      uploadStatus = statuses.find(_.statusType == UploadType.id)
      alreadyTriggered = uploadStatus.isDefined && uploadStatus.get.value == CompletedValue.value || uploadStatus.get.value == CompletedWithIssuesValue.value
      backendChecksTriggered <- if (alreadyTriggered) Future(true) else backendChecks(consignmentId, token)
      fileChecks <- if (backendChecksTriggered) {
        getFileChecksProgress(request, consignmentId)
      } else {
        throw new Exception(s"Backend checks trigger failure for consignment $consignmentId")
      }
      reference <- consignmentService.getConsignmentRef(consignmentId, token)
    } yield {
      if (fileChecks.isComplete) {
        Ok(
          views.html.fileChecksProgressAlreadyConfirmed(
            consignmentId,
            reference,
            applicationConfig.frontEndInfo,
            request.token.name,
            isJudgmentUser = false
          )
        ).uncache()
      } else {
        Ok(views.html.standard.fileChecksProgress(consignmentId, reference, applicationConfig.frontEndInfo, request.token.name))
          .uncache()
      }
    }
  }

  def judgmentFileChecksPage(consignmentId: UUID, uploadFailed: Option[String]): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val token = request.token.bearerAccessToken
    uploadFailed match {
      case Some(TRUE) => handleFailedUpload(consignmentId, token)
      case _ => for {
        statuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
        uploadStatus = statuses.find(_.statusType == UploadType.id)
        alreadyTriggered = uploadStatus.isDefined && uploadStatus.get.value == CompletedValue.value || uploadStatus.get.value == CompletedWithIssuesValue.value
        backendChecksTriggered <- if (alreadyTriggered) Future(true) else backendChecks(consignmentId, token)
        fileChecks <- if (backendChecksTriggered) {
          getFileChecksProgress(request, consignmentId)
        } else {
          throw new Exception(s"Backend checks trigger failure for consignment $consignmentId")
        }
        reference <- consignmentService.getConsignmentRef(consignmentId, token)
      } yield {
        if (fileChecks.isComplete) {
          Ok(
            views.html.fileChecksProgressAlreadyConfirmed(
              consignmentId,
              reference,
              applicationConfig.frontEndInfo,
              request.token.name,
              isJudgmentUser = true
            )
          ).uncache()
        } else {
          Ok(views.html.judgment.judgmentFileChecksProgress(consignmentId, reference, applicationConfig.frontEndInfo, request.token.name)).uncache()
        }
      }
    }
  }
}

case class FileChecksProgress(totalFiles: Int, avMetadataProgressPercentage: Int, checksumProgressPercentage: Int, ffidMetadataProgressPercentage: Int) {
  def isComplete: Boolean = avMetadataProgressPercentage == 100 && checksumProgressPercentage == 100 && ffidMetadataProgressPercentage == 100
}
