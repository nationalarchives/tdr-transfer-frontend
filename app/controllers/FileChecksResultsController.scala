package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.ExcelUtils
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses._
import services.{ConfirmTransferService, ConsignmentExportService, ConsignmentService, ConsignmentStatusService, FileCheckFailureService}
import viewsapi.Caching.preventCaching

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileChecksResultsController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val graphqlConfiguration: GraphQLConfiguration,
    val consignmentService: ConsignmentService,
    val confirmTransferService: ConfirmTransferService,
    val consignmentExportService: ConsignmentExportService,
    val consignmentStatusService: ConsignmentStatusService,
    val fileCheckFailureService: FileCheckFailureService,
    val applicationConfig: ApplicationConfig
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def fileCheckResultsPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val pageTitle = "Results of your checks"

    for {
      fileCheck <- consignmentService.getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken)
      parentFolder = fileCheck.parentFolder.getOrElse(throw new IllegalStateException(s"No parent folder found for consignment: '$consignmentId'"))
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield {
      if (fileCheck.allChecksSucceeded) {
        val consignmentInfo = ConsignmentFolderInfo(
          fileCheck.totalFiles,
          parentFolder
        )
        if (applicationConfig.blockFileChecksFailureV2) {
          Ok(
            views.html.standard.fileChecksResults(
              consignmentInfo,
              pageTitle,
              consignmentId,
              reference,
              request.token.name
            )
          )
        } else {
          Ok(
            views.html.standard.filechecks.fileChecksSuccessResults(
              consignmentInfo,
              pageTitle,
              consignmentId,
              reference,
              request.token.name
            )
          )
        }
      } else {
        val fileStatusList = fileCheck.files.flatMap(_.fileStatus)
        Ok(views.html.fileChecksResultsFailed(request.token.name, pageTitle, reference, isJudgmentUser = false, fileStatusList))
      }
    }
  }

  def judgmentFileCheckResultsPage(consignmentId: UUID, transferProgress: Option[String]): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      val pageTitle = "Results of checks"
      for {
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        result <- transferProgress match {
          case Some(CompletedValue.value)           => Future(Redirect(routes.TransferCompleteController.judgmentTransferComplete(consignmentId)).uncache())
          case Some(CompletedWithIssuesValue.value) =>
            Future(Ok(views.html.fileChecksResultsFailed(request.token.name, pageTitle, reference, isJudgmentUser = true)).uncache())
          case _ =>
            for {
              consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
              exportStatus = consignmentStatusService.getStatusValues(consignmentStatuses, ExportType).values.headOption.flatten
              result <- exportStatus match {
                case Some(InProgressValue.value) | Some(CompletedValue.value) | Some(FailedValue.value) =>
                  Future(Ok(views.html.transferAlreadyCompleted(consignmentId, reference, request.token.name, isJudgmentUser = true)).uncache())
                case None =>
                  consignmentService
                    .getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken)
                    .map(fileCheck =>
                      if (fileCheck.allChecksSucceeded) {
                        Redirect(routes.TransferCompleteController.judgmentTransferComplete(consignmentId)).uncache()
                      } else {
                        Ok(views.html.fileChecksResultsFailed(request.token.name, pageTitle, reference, isJudgmentUser = true)).uncache()
                      }
                    )
                case _ =>
                  throw new IllegalStateException(s"Unexpected Export status: $exportStatus for consignment $consignmentId")
              }
            } yield result
        }
      } yield result
  }
  def downloadFileCheckFailuresReport(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      failures <- fileCheckFailureService.getFileCheckFailures(consignmentId)
    } yield {
      val headers = List("Filepath", "Filename", "Who should investigate", "Action", "Detail", "Error Type", "File Format", "PUID")
      val dataRows: List[List[String]] = failures.flatMap { failure =>
        val fileFormats = failure.matches.map(_.formatName).mkString("|")
        val puids = failure.matches.map(_.puid).mkString("|")
        if (failure.statusActions.isEmpty) {
          List(List(failure.originalPath, failure.filename, "", "", "", "", fileFormats, puids))
        } else {
          failure.statusActions.map { action =>
            List(failure.originalPath, failure.filename, action.actionType, action.action, action.message, action.statusName, fileFormats, puids)
          }
        }
      }
      val rows = headers :: dataRows
      val excelBytes = ExcelUtils.writeExcel(s"File Check Failures for $reference", rows)
      Ok(excelBytes)
        .as("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .withHeaders("Content-Disposition" -> s"attachment; filename=$reference-file-check-failures-${fileCheckFailuresCurrentDateTime}.xlsx")
    }
  }

  private def fileCheckFailuresCurrentDateTime: String = {
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
    dateTimeFormatter.format(LocalDateTime.now())
  }
}

case class ConsignmentFolderInfo(numberOfFiles: Int, parentFolder: String)
