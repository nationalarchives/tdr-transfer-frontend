package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.CsvUtils
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TransferCompleteController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def transferComplete(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map { consignmentReference =>
        Ok(views.html.standard.transferComplete(consignmentId, consignmentReference, request.token.name))
      }
  }

  def judgmentTransferComplete(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map { consignmentReference =>
        Ok(views.html.judgment.judgmentComplete(consignmentReference, request.token.name))
      }
  }

  def downloadReport(consignmentId: UUID, consignmentRef: String): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val headers = List("Filepath", "FileName", "FileType", "Filesize", "RightsCopyright", "LegalStatus", "HeldBy", "Language", "FoiExemptionCode", "LastModified", "ExportDatetime")
    consignmentService.getConsignmentExport(consignmentId, request.token.bearerAccessToken)
    for {
      consignmentExport <- consignmentService.getConsignmentExport(consignmentId, request.token.bearerAccessToken)
      exportDateTime = consignmentExport.exportDatetime
    } yield {
      val rows = consignmentExport.files.foldLeft(List[List[String]]()) { case (record, file) =>
        record :+ List(
          file.metadata.clientSideOriginalFilePath.getOrElse(""),
          file.fileName.getOrElse(""),
          file.fileType.getOrElse(""),
          file.metadata.clientSideFileSize.map(_.toString).getOrElse(""),
          file.metadata.rightsCopyright.getOrElse(""),
          file.metadata.legalStatus.getOrElse(""),
          file.metadata.heldBy.getOrElse(""),
          file.metadata.language.getOrElse(""),
          file.metadata.foiExemptionCode.getOrElse(""),
          file.metadata.clientSideLastModifiedDate.map(_.toString).getOrElse(""),
          exportDateTime.map(_.toString).getOrElse("")
        )
      }
      val csvString = CsvUtils.writeCsv(headers :: rows)
      Ok(csvString)
        .as("text/csv")
        .withHeaders("Content-Disposition" -> s"attachment; filename=$consignmentRef.csv")
    }
  }
}
