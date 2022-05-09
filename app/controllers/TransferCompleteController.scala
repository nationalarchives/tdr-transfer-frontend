package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TransferCompleteController @Inject()(val controllerComponents: SecurityComponents,
                                           val keycloakConfiguration: KeycloakConfiguration,
                                           val consignmentService: ConsignmentService)
                                          (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  def transferComplete(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map { consignmentReference =>
        Ok(views.html.standard.transferComplete(consignmentReference, request.token.name))
      }
  }

  def judgmentTransferComplete(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map { consignmentReference =>
        Ok(views.html.judgment.judgmentComplete(consignmentReference, request.token.name))
      }
  }

  def downloadReport(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val header = "Filepath,FileName,FileType,Filesize,RightsCopyright,LegalStatus,HeldBy,Language,FoiExemptionCode,LastModified"
    consignmentService.getConsignmentExport(consignmentId, request.token.bearerAccessToken)
      .map { result =>
        val rows = result.foldLeft(List[String]()) {
          case (record, file) => record :+ ReportCsv(
            file.metadata.clientSideOriginalFilePath,
            file.fileName,
            file.fileType,
            file.metadata.clientSideFileSize,
            file.metadata.rightsCopyright,
            file.metadata.legalStatus,
            file.metadata.heldBy,
            file.metadata.language,
            file.metadata.foiExemptionCode,
            file.metadata.clientSideLastModifiedDate
          ).toCSV
        }

        Ok(header + "\n" + rows.mkString("\n"))
          .as("text/csv")
          .withHeaders(
            "Content-Disposition" -> "attachment; filename=report.csv"
          )
      }

  }

  implicit class CSVWrapper(val prod: Product) {
    def toCSV: String = prod.productIterator.map {
      case Some(value) => value
      case None => ""
      case rest => rest
    }.mkString(",")
  }

  case class ReportCsv(filepath: Option[String],
                       filename: Option[String],
                       filetype: Option[String],
                       filesize: Option[Long],
                       rightsCopyright: Option[String],
                       legalStatus: Option[String],
                       heldBy: Option[String],
                       language: Option[String],
                       exemptionCode: Option[String],
                       lastModified: Option[LocalDateTime])
}
