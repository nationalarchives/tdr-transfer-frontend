package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.ExcelUtils
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConfirmTransferService, ConsignmentExportService, ConsignmentService, ConsignmentStatusService, FileCheckFailureService}
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileChecksFailuresController @Inject() (
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

  def fileChecksFailures(consignmentId: UUID, consignmentRef: String): Action[AnyContent] = tnaUserAction { implicit request: Request[AnyContent] =>
    Future.successful(Ok(views.html.tna.fileChecksFailures(consignmentId, consignmentRef, request.token.name)))
  }

  def downloadFileCheckFailuresReport(consignmentId: UUID, consignmentRef: String): Action[AnyContent] = tnaUserAction { implicit request: Request[AnyContent] =>
    val reference = s"$consignmentId-$consignmentRef"
    fileCheckFailureService.getFileCheckFailures(consignmentId).map { failures =>
      val headers = List("StatusType", "StatusValue", "Filepath", "Filename", "Action Required", "Error Details", "File Format", "PUID")
      val dataRows: List[List[String]] = failures.flatMap { failure =>
        val fileFormats = failure.matches.flatMap(_.formatName).mkString("|")
        val puids = failure.matches.flatMap(_.puid).mkString("|")
        if (failure.statusActions.isEmpty) {
          List(List("", "", failure.originalPath, failure.filename, "", "", fileFormats, puids))
        } else {
          failure.statusActions.map { action =>
            List(action.statusName, action.statusValue, failure.originalPath, failure.filename, action.action, action.message, fileFormats, puids)
          }
        }
      }
      val rows = headers :: dataRows
      val excelBytes = ExcelUtils.writeExcel(s"File Check Failures for $reference", rows, hiddenColumns = Set(0, 1))
      Ok(excelBytes)
        .as("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        .withHeaders("Content-Disposition" -> s"attachment; filename=$reference-file-check-failures-$fileCheckFailuresCurrentDateTime.xlsx")
    }
  }

  private def fileCheckFailuresCurrentDateTime: String = {
    val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
    dateTimeFormatter.format(LocalDateTime.now())
  }
}
