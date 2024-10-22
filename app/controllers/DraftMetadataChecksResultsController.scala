package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import controllers.util.ExcelUtils
import controllers.util.MetadataProperty.filePath
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.{I18nSupport, Lang, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request}
import services.FileError.SCHEMA_VALIDATION
import services.Statuses._
import services.{FileError, _}
import viewsapi.Caching.preventCaching

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DraftMetadataChecksResultsController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val applicationConfig: ApplicationConfig,
    val consignmentStatusService: ConsignmentStatusService,
    val draftMetadataService: DraftMetadataService,
    val messages: MessagesApi
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def draftMetadataChecksResultsPage(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    if (applicationConfig.blockDraftMetadataUpload) {
      Future(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
    } else {
      val token = request.token.bearerAccessToken
      for {
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
        errorType <- getErrorType(consignmentStatuses, consignmentId)
      } yield {
        val resultsPage = {
          // leaving original page for no errors
          if (errorType == FileError.NONE) {
            views.html.draftmetadata
              .draftMetadataChecksResults(consignmentId, reference, DraftMetadataProgress("IMPORTED", "blue"), request.token.name)
          } else {
            if (isErrorReportAvailable(errorType)) {
              views.html.draftmetadata
                .draftMetadataChecksWithErrorDownload(
                  consignmentId,
                  reference,
                  request.token.name,
                  actionMessage(errorType),
                  detailsMessage(errorType)
                )
            } else {
              views.html.draftmetadata
                .draftMetadataChecksErrorsNoDownload(
                  consignmentId,
                  reference,
                  request.token.name,
                  actionMessage(errorType),
                  detailsMessage(errorType)
                )
            }
          }
        }
        Ok(resultsPage).uncache()
      }
    }
  }

  implicit val defaultLang: Lang = Lang.defaultLang

  private def actionMessage(fileError: FileError.FileError): String = {
    val key = s"draftMetadata.validation.action.$fileError"
    if (messages.isDefinedAt(key))
      messages(key)
    else
      s"Require action message for $key"
  }

  private def detailsMessage(fileError: FileError.FileError): String = {
    val key = s"draftMetadata.validation.details.$fileError"
    if (messages.isDefinedAt(key))
      messages(key)
    else
      s"Require details message for $key"
  }

  private def isErrorReportAvailable(fileError: FileError.FileError): Boolean = {
    fileError match {
      case SCHEMA_VALIDATION => true
      case _                 => false
    }
  }

  private def getErrorType(consignmentStatuses: List[GetConsignment.ConsignmentStatuses], consignmentId: UUID): Future[FileError.Value] = {
    val draftMetadataStatus = consignmentStatuses.find(_.statusType == DraftMetadataType.id).map(_.value)
    if (draftMetadataStatus.isDefined) {
      draftMetadataStatus.get match {
        case CompletedValue.value => Future.successful(FileError.NONE)
        case FailedValue.value    => Future.successful(FileError.UNKNOWN)
        case _                    => draftMetadataService.getErrorTypeFromErrorJson(consignmentId)
      }
    } else {
      Future.successful(FileError.UNKNOWN)
    }
  }

  def downloadErrorReport(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      errorReport <- draftMetadataService.getErrorReport(consignmentId)
    } yield {
      val errorList = errorReport.fileError match {
        case SCHEMA_VALIDATION =>
          errorReport.validationErrors.flatMap { validationErrors =>
            val data = validationErrors.data.map(metadata => metadata.name -> metadata.value).toMap
            validationErrors.errors.map(error => List(data(filePath), error.property, data(error.property), error.message))
          }
        case _ => Nil
      }
      val header: List[String] = List(filePath, "Field", "Value", "Error Message")
      val excelFile = ExcelUtils.writeExcel(s"Error report for $reference", header :: errorList)
      val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss")
      val currentDateTime = dateTimeFormatter.format(LocalDateTime.now())
      val excelContentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
      Ok(excelFile)
        .as(excelContentType)
        .withHeaders("Content-Disposition" -> s"attachment; filename=ErrorReport-${reference}-$currentDateTime.xlsx")
    }
  }
}

case class DraftMetadataProgress(value: String, colour: String)
