package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses._
import services._
import viewsapi.Caching.preventCaching

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
    val draftMetadataService: DraftMetadataService
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
        errorType <- if (errorTypeDeterminedFromFile(consignmentStatuses)) draftMetadataService.getErrorType(consignmentId) else Future.successful(FileError.UNKNOWN)
      } yield {
        val resultsPage = {
          // leaving original page for no errors
          if (getValue(consignmentStatuses, DraftMetadataType).value == "IMPORTED") {
            views.html.draftmetadata
              .draftMetadataChecksResults(consignmentId, reference, getValue(consignmentStatuses, DraftMetadataType), request.token.name)
          } else {
            if (isErrorReportAvailable(errorType)) {
              views.html.draftmetadata
                .draftMetadataChecksWithErrorDownload(
                  consignmentId,
                  reference,
                  getValue(consignmentStatuses, DraftMetadataType),
                  request.token.name,
                  actionMessage(errorType),
                  detailsMessage(errorType)
                )
            } else {
              views.html.draftmetadata
                .draftMetadataChecksErrorsNoDownload(
                  consignmentId,
                  reference,
                  getValue(consignmentStatuses, DraftMetadataType),
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

  private def actionMessage(fileError: FileError.FileError)(implicit messages: Messages): String = {
    val key = s"draftMetadata.validation.action.$fileError"
    if (Messages.isDefinedAt(key))
      Messages(key)
    else
      s"Require action message for $key"
  }

  private def detailsMessage(fileError: FileError.FileError)(implicit messages: Messages): String = {
    val key = s"draftMetadata.validation.details.$fileError"
    if (Messages.isDefinedAt(key))
      Messages(key)
    else
      s"Require details message for $key"
  }

  private def isErrorReportAvailable(fileError: FileError.FileError): Boolean = {
    fileError match {
      case FileError.SCHEMA_VALIDATION => true
      case _                           => false
    }
  }

  private def errorTypeDeterminedFromFile(statuses: List[GetConsignment.ConsignmentStatuses]): Boolean = {
    val draftMetadataProgress = getValue(statuses, DraftMetadataType)
    draftMetadataProgress.value match {
      case "IMPORTED" | "FAILED" => false
      case _                     => true
    }
  }

  def getValue(statuses: List[GetConsignment.ConsignmentStatuses], statusType: StatusType): DraftMetadataProgress = {
    val failed = DraftMetadataProgress("FAILED", "red")
    statuses.find(_.statusType == statusType.id).map(_.value).map {
      case FailedValue.value              => failed
      case CompletedWithIssuesValue.value => DraftMetadataProgress("ERRORS", "red")
      case CompletedValue.value           => DraftMetadataProgress("IMPORTED", "blue")
      case _                              => failed
    } getOrElse failed
  }
}

case class DraftMetadataProgress(value: String, colour: String)
