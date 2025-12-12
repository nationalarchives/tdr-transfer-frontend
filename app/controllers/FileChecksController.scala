package controllers

import auth.TokenSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.types.ConsignmentStatusInput
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
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
    val graphqlConfiguration: GraphQLConfiguration,
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

  implicit val jsonEncoder: Encoder[TransferProgress] = deriveEncoder[TransferProgress]

  private def getFileCheckStatuses(statuses: List[ConsignmentStatuses]) = {
    consignmentStatusService.getStatusValues(statuses, ClientChecksType, ServerAntivirusType, ServerChecksumType, ServerFFIDType, ServerRedactionType)
  }

  private def fileChecksCompleted(consignmentStatuses: List[ConsignmentStatuses]): Boolean = {
    val fileCheckStatuses = getFileCheckStatuses(consignmentStatuses)
    val clientChecksStatus = fileCheckStatuses(ClientChecksType).getOrElse("")
    clientChecksStatus.nonEmpty && clientChecksStatus != InProgressValue.value
  }

  private def allChecksSucceeded(consignmentStatuses: List[ConsignmentStatuses]): Boolean = {
    val statusValues = getFileCheckStatuses(consignmentStatuses).values.flatten
    statusValues.size == clientChecksStatuses.size && statusValues.forall(_ == CompletedValue.value)
  }

  private def getFileChecksProgress(request: Request[AnyContent], consignmentId: UUID)(implicit requestHeader: RequestHeader): Future[FileChecksProgress] = {
    for {
      statuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
      completed = fileChecksCompleted(statuses)
      checksSucceeded = allChecksSucceeded(statuses)
    } yield FileChecksProgress(checksSucceeded, completed)
  }

  private def handleFailedUpload(consignmentId: UUID, consignmentRef: String, isJudgmentUser: Boolean, token: BearerAccessToken)(implicit request: Request[AnyContent]) = {
    for {
      _ <- consignmentStatusService.updateConsignmentStatus(ConsignmentStatusInput(consignmentId, UploadType.id, Some(CompletedWithIssuesValue.value), None), token)
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
      _ <- consignmentStatusService.updateConsignmentStatus(ConsignmentStatusInput(consignmentId, UploadType.id, Some(uploadStatusUpdate.value), None), token)
    } yield backendChecksTriggered
  }

  def fileCheckProgress(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request =>
    for {
      statuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
    } yield {
      val checksCompleted = fileChecksCompleted(statuses)
      Ok(checksCompleted.asJson.noSpaces)
    }
  }

  def transferProgress(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request =>
    for {
      statuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
      result = statuses match {
        case s if fileChecksCompleted(s) && allChecksSucceeded(s) =>
          val exportStatus = consignmentStatusService.getStatusValues(s, ExportType).values.headOption.flatten
          TransferProgress(CompletedValue.value, exportStatus.getOrElse(""))
        case s if !fileChecksCompleted(s) => TransferProgress(InProgressValue.value)
        case s if fileChecksCompleted(s) && !allChecksSucceeded(s) => TransferProgress(CompletedWithIssuesValue.value)
//        case _ => TransferProgress(InProgressValue.value)
      }
    } yield Ok(result.asJson.noSpaces)
  }

  def fileChecksPage(consignmentId: UUID, uploadFailed: Option[String]): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val token = request.token.bearerAccessToken
    for {
      reference <- consignmentService.getConsignmentRef(consignmentId, token)
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
        reference <- consignmentService.getConsignmentRef(consignmentId, token)
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
      statuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
      uploadStatus = statuses.find(_.statusType == UploadType.id)
      alreadyTriggered = uploadStatus.isDefined && uploadStatus.get.value == CompletedValue.value || uploadStatus.get.value == CompletedWithIssuesValue.value
      backendChecksTriggered <- if (alreadyTriggered) Future.successful(true) else triggerBackendChecks(consignmentId, token)
      fileChecks <-
        if (backendChecksTriggered && uploadStatus.get.value != CompletedWithIssuesValue.value) {
          getFileChecksProgress(request, consignmentId)
        } else {
          throw new Exception(s"Backend checks trigger failure for consignment $consignmentId")
        }
    } yield {
      if (fileChecks.completed) {
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

  private def judgmentTransferCompleted(exportStatus: Option[String]): Boolean = {
    exportStatus match {
      case Some(InProgressValue.value) | Some(CompletedValue.value) | Some(FailedValue.value) => true
      case _ => false
    }
  }

  private def JudgmentCompleteTransfer(consignmentId: UUID)(implicit request: Request[AnyContent]): Future[String] = {
    for {
      statuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
      exportStatus = consignmentStatusService.getStatusValues(statuses, ExportType).values.headOption.flatten
      r <- exportStatus match {
        case Some(InProgressValue.value) | Some(CompletedValue.value) | Some(FailedValue.value) => Future("TransferAlreadyCompleted")
        case None if allChecksSucceeded(statuses) =>
          val token: BearerAccessToken = request.token.bearerAccessToken
          val legalCustodyTransferConfirmation = FinalTransferConfirmationData(transferLegalCustody = true)
          for {
            _ <- confirmTransferService.addFinalTransferConfirmation(consignmentId, token, legalCustodyTransferConfirmation)
            _ <- consignmentExportService.updateTransferInitiated(consignmentId, request.token.bearerAccessToken)
            _ <- consignmentExportService.triggerExport(consignmentId, request.token.bearerAccessToken.toString)
          } yield "Completed"
        case None if !allChecksSucceeded(statuses) => Future("FileChecksFailed")
        case _ => throw new IllegalStateException(s"Unexpected Export status: $exportStatus for consignment $consignmentId")
      }
    } yield r


//      result <- exportStatus match {
//        case Some(InProgressValue.value) | Some(CompletedValue.value) | Some(FailedValue.value) =>
//          Future("TransferAlreadyCompleted")
//        case None =>
//          for {
//            fileCheck <- consignmentService.fileCheckProgress(consignmentId, request.token.bearerAccessToken)
//            result <-
//              if (fileCheck.allChecksSucceeded) {
//                val token: BearerAccessToken = request.token.bearerAccessToken
//                val legalCustodyTransferConfirmation = FinalTransferConfirmationData(transferLegalCustody = true)
//                for {
//                  _ <- confirmTransferService.addFinalTransferConfirmation(consignmentId, token, legalCustodyTransferConfirmation)
//                  _ <- consignmentExportService.updateTransferInitiated(consignmentId, request.token.bearerAccessToken)
//                  _ <- consignmentExportService.triggerExport(consignmentId, request.token.bearerAccessToken.toString)
//                } yield "Completed"
//              } else {
//                Future("FileChecksFailed")
//              }
//          } yield result
//        case _ =>
//          throw new IllegalStateException(s"Unexpected Export status: $exportStatus for consignment $consignmentId")
//      }
//    } yield result
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
          if (progress.completed) {
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
    completed: Boolean
)

case class TransferProgress(fileChecksStatus: String, exportStatus: String = "")
