package controllers

import auth.TokenSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.Statuses.{CompletedValue, ExportType, FailedValue, InProgressValue}
import services.{ConfirmTransferService, ConsignmentExportService, ConsignmentService, ConsignmentStatusService}
import viewsapi.Caching.preventCaching

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
    val applicationConfig: ApplicationConfig
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def fileCheckResultsPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
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
        Ok(
          views.html.standard.fileChecksResults(
            consignmentInfo,
            pageTitle,
            consignmentId,
            reference,
            request.token.name,
            applicationConfig.blockDraftMetadataUpload
          )
        )
      } else {
        val fileStatusList = fileCheck.files.flatMap(_.fileStatus)
        Ok(views.html.fileChecksResultsFailed(request.token.name, pageTitle, reference, isJudgmentUser = false, fileStatusList))
      }
    }
  }

  def judgmentFileCheckResultsPage(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val pageTitle = "Results of checks"
    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      exportStatus = consignmentStatusService.getStatusValues(consignmentStatuses, ExportType).values.headOption.flatten
      result <- exportStatus match {
        case Some(InProgressValue.value) | Some(CompletedValue.value) | Some(FailedValue.value) =>
          Future(Ok(views.html.transferAlreadyCompleted(consignmentId, reference, request.token.name, isJudgmentUser = true)).uncache())
        case None =>
          for {
            fileCheck <- consignmentService.getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken)
            result <-
              if (fileCheck.allChecksSucceeded) {
                val token: BearerAccessToken = request.token.bearerAccessToken
                val legalCustodyTransferConfirmation = FinalTransferConfirmationData(transferLegalCustody = true)
                for {
                  _ <- confirmTransferService.addFinalTransferConfirmation(consignmentId, token, legalCustodyTransferConfirmation)
                  _ <- consignmentExportService.updateTransferInitiated(consignmentId, request.token.bearerAccessToken)
                  _ <- consignmentExportService.triggerExport(consignmentId, request.token.bearerAccessToken.toString)
                  res <- Future(Redirect(routes.TransferCompleteController.judgmentTransferComplete(consignmentId)).uncache())
                } yield res
              } else {
                Future(Ok(views.html.fileChecksResultsFailed(request.token.name, pageTitle, reference, isJudgmentUser = true)).uncache())
              }
          } yield result
        case _ =>
          throw new IllegalStateException(s"Unexpected Export status: $exportStatus for consignment $consignmentId")
      }
    } yield result
  }
}

case class ConsignmentFolderInfo(numberOfFiles: Int, parentFolder: String)
