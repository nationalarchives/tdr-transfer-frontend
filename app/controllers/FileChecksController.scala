package controllers

import auth.TokenSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import io.circe.generic.auto._
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.Statuses._
import services._
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future, blocking}

@Singleton
class FileChecksController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val applicationConfig: ApplicationConfig,
    val backendChecksService: BackendChecksService,
    val consignmentStatusService: ConsignmentStatusService,
    val confirmTransferService: ConfirmTransferService,
    val consignmentExportService: ConsignmentExportService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  private def getFileChecksProgress(request: Request[AnyContent], consignmentId: UUID)(implicit requestHeader: RequestHeader): Future[FileChecksProgress] = {
    consignmentService
      .getConsignmentFileChecks(consignmentId, request.token)
      .map { fileCheckProgress =>
        if (fileCheckProgress.allChecksSucceeded) {
          consignmentStatusService.updateConsignmentStatus(consignmentId, "ClientChecks", "Completed")
        }
        FileChecksProgress(
          fileCheckProgress.allChecksSucceeded,
          fileCheckProgress.totalFiles,
          fileCheckProgress.avProcessed * 100 / fileCheckProgress.totalFiles,
          fileCheckProgress.checksumProcessed * 100 / fileCheckProgress.totalFiles,
          fileCheckProgress.fileFormatProcessed * 100 / fileCheckProgress.totalFiles
        )
      }
  }

  private def handleFailedUpload(consignmentId: UUID, consignmentRef: String, isJudgmentUser: Boolean, token: BearerAccessToken)(implicit request: Request[AnyContent]) = {
    for {
      _ <- consignmentStatusService.updateConsignmentStatus(consignmentId, UploadType.id, CompletedWithIssuesValue.value)
    } yield {
      Ok(views.html.uploadInProgress(consignmentId, consignmentRef, "Uploading your records", request.token.name, isJudgmentUser))
        .uncache()
    }
  }

  private def triggerBackendChecks(consignmentId: UUID, token: BearerAccessToken): Future[Boolean] = {
    for {
      backendChecksTriggered <- backendChecksService.triggerBackendChecks(consignmentId, token.getValue)
      uploadStatusUpdate =
        if (backendChecksTriggered) {
          CompletedValue
        } else {
          CompletedWithIssuesValue
        }
      _ <- consignmentStatusService.updateConsignmentStatus(consignmentId, UploadType.id, uploadStatusUpdate.value)
    } yield backendChecksTriggered
  }

  def fileCheckProgress(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request =>
    consignmentService
      .getConsignmentFileChecks(consignmentId, request.token)
      .map(_.asJson.noSpaces)
      .map(Ok(_))
  }

  def transferProgress(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request =>
    for {
      fileCheck <- getFileChecksProgress(request, consignmentId)
      result <- {
        if (fileCheck.isComplete) {
          if (fileCheck.allChecksSucceeded) {
            for {
              consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId)
              exportStatus = consignmentStatusService.getStatusValues(consignmentStatuses, ExportType).values.headOption.flatten
            } yield TransferProgress(CompletedValue.value, exportStatus.getOrElse(""))
          } else {
            Future(TransferProgress(CompletedWithIssuesValue.value))
          }
        } else {
          Future(TransferProgress(InProgressValue.value))
        }
      }
    } yield Ok(result.asJson.noSpaces)
  }

  def fileChecksPage(consignmentId: UUID, uploadFailed: Option[String]): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val token = request.token.bearerAccessToken
    for {
      reference <- consignmentService.getConsignmentRef(consignmentId)
      result <- uploadFailed match {
        case Some("true") => handleFailedUpload(consignmentId, reference, isJudgmentUser = false, token)
        case _            => handleSuccessfulUpload(consignmentId, reference, isJudgmentUser = false)
      }
    } yield result
  }

  def judgmentFileChecksPage(consignmentId: UUID, uploadFailed: Option[String]): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      val token = request.token.bearerAccessToken
      for {
        reference <- consignmentService.getConsignmentRef(consignmentId)
        result <- uploadFailed match {
          case Some("true") => handleFailedUpload(consignmentId, reference, isJudgmentUser = true, token)
          case _            => handleSuccessfulUpload(consignmentId, reference, isJudgmentUser = true)
        }
      } yield result
  }

  def judgmentCompleteTransfer(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      _ <- waitForFileChecksToBeCompleted(consignmentId)
      result <- JudgmentCompleteTransfer(consignmentId)
    } yield Ok(result)
  }

  private def handleSuccessfulUpload(consignmentId: UUID, reference: String, isJudgmentUser: Boolean)(implicit request: Request[AnyContent]) = {
    val token = request.token.bearerAccessToken
    (for {
      statuses <- consignmentStatusService.getConsignmentStatuses(consignmentId)
      uploadStatus = statuses.find(_.statusType == UploadType.id)
      alreadyTriggered = uploadStatus.isDefined && uploadStatus.get.value == CompletedValue.value || uploadStatus.get.value == CompletedWithIssuesValue.value
      fileChecks <-
        if (uploadStatus.get.value != CompletedWithIssuesValue.value) {
          getFileChecksProgress(request, consignmentId)
        } else {
          throw new Exception(s"Backend checks trigger failure for consignment $consignmentId")
        }
    } yield {
      if (fileChecks.isComplete) {
        Ok(
          views.html.fileChecksProgressAlreadyConfirmed(
            consignmentId,
            reference,
            applicationConfig.frontEndInfo,
            request.token.name,
            isJudgmentUser
          )
        ).uncache()
      } else {
        if (isJudgmentUser) {
          Ok(views.html.judgment.judgmentFileChecksProgress(consignmentId, reference, applicationConfig.frontEndInfo, request.token.name)).uncache()
        } else {
          Ok(views.html.standard.fileChecksProgress(consignmentId, reference, applicationConfig.frontEndInfo, request.token.name))
            .uncache()
        }
      }
    }).recover { case exception: Exception =>
      Ok(views.html.uploadInProgress(consignmentId, reference, "Uploading your records", request.token.name, isJudgmentUser)).uncache()
    }
  }

  private def JudgmentCompleteTransfer(consignmentId: UUID)(implicit request: Request[AnyContent]): Future[String] = {
    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId)
      exportStatus = consignmentStatusService.getStatusValues(consignmentStatuses, ExportType).values.headOption.flatten
      result <- exportStatus match {
        case Some(InProgressValue.value) | Some(CompletedValue.value) | Some(FailedValue.value) =>
          Future("TransferAlreadyCompleted")
        case None =>
          for {
            fileCheck <- consignmentService.getConsignmentFileChecks(consignmentId, request.token)
            result <-
              if (fileCheck.allChecksSucceeded) {
                val token: BearerAccessToken = request.token.bearerAccessToken
                val legalCustodyTransferConfirmation = FinalTransferConfirmationData(transferLegalCustody = true)
                for {
                  _ <- confirmTransferService.addFinalTransferConfirmation(consignmentId, legalCustodyTransferConfirmation)
                  _ <- consignmentExportService.updateTransferInitiated(consignmentId, request.token.bearerAccessToken)
                  _ <- consignmentExportService.triggerExport(consignmentId, request.token)
                } yield "Completed"
              } else {
                Future("FileChecksFailed")
              }
          } yield result
        case _ =>
          throw new IllegalStateException(s"Unexpected Export status: $exportStatus for consignment $consignmentId")
      }
    } yield result
  }

  private def waitForFileChecksToBeCompleted(consignmentId: UUID)(implicit request: Request[AnyContent]): Future[Unit] = {
    val totalSleepTime = applicationConfig.fileChecksTotalTimoutInSeconds * 1000 // Total sleep time in milliseconds
    val interval = 5 * 1000 // Interval time in milliseconds (5 seconds)
    val intervals = totalSleepTime / interval

    def checkStatus(remainingIntervals: Int): Future[Unit] = {
      if (remainingIntervals <= 0) {
        Future.unit
      } else {
        getFileChecksProgress(request, consignmentId).flatMap { progress =>
          if (progress.isComplete) {
            Future.unit
          } else {
            Future {
              blocking(Thread.sleep(interval))
            }.flatMap(_ => checkStatus(remainingIntervals - 1))
          }
        }
      }
    }

    checkStatus(intervals)
  }
}

case class FileChecksProgress(
    allChecksSucceeded: Boolean,
    totalFiles: Int,
    avMetadataProgressPercentage: Int,
    checksumProgressPercentage: Int,
    ffidMetadataProgressPercentage: Int
) {
  def isComplete: Boolean = avMetadataProgressPercentage == 100 && checksumProgressPercentage == 100 && ffidMetadataProgressPercentage == 100
}
case class TransferProgress(fileChecksStatus: String, exportStatus: String = "")
