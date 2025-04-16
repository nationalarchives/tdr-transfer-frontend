package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import controllers.util.ExcelUtils
import controllers.util.MetadataProperty.filePath
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.{I18nSupport, Lang, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request}
import services.FileError.{FileError, ROW_VALIDATION, SCHEMA_VALIDATION, UTF_8}
import services.Statuses._
import services._
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
        consignmentDetails <- consignmentService.getConsignmentDetails(consignmentId, token)
        reference = consignmentDetails.consignmentReference
        statuses = consignmentDetails.consignmentStatuses
        uploadedFileName = consignmentDetails.clientSideDraftMetadataFileName.getOrElse("Not Available")
        draftMetadataStatus = statuses.find(_.statusType == DraftMetadataType.id).map(_.value)
        errorReport <- getErrorReport(draftMetadataStatus, consignmentId)
        errorType = getErrorType(errorReport, draftMetadataStatus)
      } yield {
        val resultsPage = {
          // leaving original page for no errors
          if (errorType == FileError.NONE) {
            views.html.draftmetadata
              .draftMetadataChecksResults(consignmentId, reference, DraftMetadataProgress("IMPORTED", "blue"), request.token.name, uploadedFileName)
          } else {
            if (isErrorReportAvailable(errorType)) {
              views.html.draftmetadata
                .draftMetadataChecksWithErrorDownload(
                  consignmentId,
                  reference,
                  request.token.name,
                  actionMessage(errorType),
                  detailsMessage(errorType),
                  uploadedFileName
                )
            } else {
              views.html.draftmetadata
                .draftMetadataChecksErrorsNoDownload(
                  consignmentId,
                  reference,
                  request.token.name,
                  actionMessage(errorType),
                  detailsMessage(errorType),
                  getAffectedProperties(errorReport),
                  uploadedFileName
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

  private def getAffectedProperties(errorReport: Option[ErrorFileData]): Set[String] = {
    errorReport match {
      case Some(er)
          if er.fileError == FileError.SCHEMA_REQUIRED
            || er.fileError == FileError.DUPLICATE_HEADER =>
        er.validationErrors.flatMap(_.errors.map(_.property)).toSet
      case _ => Set()
    }
  }

  private def isErrorReportAvailable(fileError: FileError.FileError): Boolean = {
    fileError match {
      case SCHEMA_VALIDATION => true
      case _                 => false
    }
  }

  private def getErrorReport(draftMetadataStatus: Option[String], consignmentId: UUID): Future[Option[ErrorFileData]] = {
    draftMetadataStatus match {
      case Some(s) if s == CompletedValue.value || s == FailedValue.value => Future.successful(None)
      case Some(_)                                                        => draftMetadataService.getErrorReport(consignmentId).map(Some(_))
      case None                                                           => Future.successful(None)
    }
  }

  private def getErrorType(errorReport: Option[ErrorFileData], draftMetadataStatus: Option[String]): FileError.Value = {
    draftMetadataStatus match {
      case Some(s) if s == CompletedValue.value => FileError.NONE
      case Some(s) if s == FailedValue.value    => FileError.UNKNOWN
      case Some(_) if errorReport.isDefined     => errorReport.get.fileError
      case _                                    => FileError.UNKNOWN
    }
  }

  def downloadErrorReport(consignmentId: UUID, priorityErrorType: FileError = ROW_VALIDATION): Action[AnyContent] = standardUserAndTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      for {
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        errorReport <- draftMetadataService.getErrorReport(consignmentId)
      } yield {
        val errorPriorityOrdering: Ordering[Error] =
          Ordering.by[Error, (Boolean, String)](e => (!e.validationProcess.equals(s"$priorityErrorType"), e.validationProcess))
        val errorList = errorReport.fileError match {
          case SCHEMA_VALIDATION =>
            errorReport.validationErrors.flatMap { validationErrors =>
              val data = validationErrors.data.map(metadata => metadata.name -> metadata.value).toMap
              validationErrors.errors.toList
                .sorted(errorPriorityOrdering)
                .map(error => List(validationErrors.assetId, error.property, data.getOrElse(error.property, ""), error.message))
            }
          case _ => Nil
        }
        val header: List[String] = List(filePath, "Column", "Value", "Error Message")
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
