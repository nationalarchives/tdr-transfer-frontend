package controllers

import auth.TokenSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping}
import play.api.i18n.{I18nSupport, Lang, Langs}
import play.api.mvc._
import play.api.mvc.Results._
import services.Statuses.{ClientChecksType, CompletedValue, ExportType, FailedValue, InProgressValue, SeriesType, TransferAgreementType, UploadType}
import services.{ConfirmTransferService, ConsignmentExportService, ConsignmentService, ConsignmentStatusService, Statuses}
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class ConfirmTransferController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val confirmTransferService: ConfirmTransferService,
    val consignmentExportService: ConsignmentExportService,
    val consignmentStatusService: ConsignmentStatusService,
    langs: Langs
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  implicit val language: Lang = langs.availables.head
  val finalTransferConfirmationForm: Form[FinalTransferConfirmationData] = Form(
    mapping(
      "transferLegalCustody" -> boolean
        .verifying("Transferral of legal custody of all records must be confirmed before proceeding", b => b)
    )(FinalTransferConfirmationData.apply)(FinalTransferConfirmationData.unapply)
  )

  private def getConsignmentSummary(request: Request[AnyContent], consignmentId: UUID)(implicit requestHeader: RequestHeader): Future[ConsignmentSummaryData] = {
    consignmentService
      .getConsignmentConfirmTransfer(consignmentId, request.token.bearerAccessToken)
      .map { summary =>
        ConsignmentSummaryData(summary.seriesName.get, summary.transferringBodyName.get, summary.totalFiles, summary.consignmentReference)
      }
  }

  private def loadStandardPageBasedOnCtStatus(consignmentId: UUID, httpStatus: Status, finalTransferForm: Form[FinalTransferConfirmationData] = finalTransferConfirmationForm)(
      implicit request: Request[AnyContent]
  ): Future[Result] = {
    consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken).flatMap { consignmentStatuses =>
      val consignmentStatusValues: Map[Statuses.StatusType, Option[String]] =
        consignmentStatusService.getStatusValues(consignmentStatuses, SeriesType, TransferAgreementType, UploadType, ClientChecksType, ExportType)
      val exportTransferStatus = consignmentStatusValues.get(ExportType).headOption.flatten
      val incompleteStatuses = consignmentStatusValues.filter { case (_, statusValue) => !statusValue.contains(CompletedValue.value) }

      Seq(SeriesType, TransferAgreementType, UploadType, ClientChecksType)
        .find(statusType => incompleteStatuses.contains(statusType))
        .map {
          case SeriesType            => Future(Redirect(routes.SeriesDetailsController.seriesDetails(consignmentId)))
          case TransferAgreementType => Future(Redirect(routes.TransferAgreementPart1Controller.transferAgreement(consignmentId)))
          case UploadType            => Future(Redirect(routes.UploadController.uploadPage(consignmentId)))
          case ClientChecksType      => Future(Redirect(routes.FileChecksController.fileChecksPage(consignmentId, Some("false"))))
        }
        .getOrElse(
          exportTransferStatus match {
            case Some(InProgressValue.value) | Some(CompletedValue.value) | Some(FailedValue.value) =>
              consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken).map { consignmentRef =>
                Ok(views.html.transferAlreadyCompleted(consignmentId, consignmentRef, request.token.name)).uncache()
              }
            case None =>
              getConsignmentSummary(request, consignmentId).map { consignmentSummary =>
                httpStatus(views.html.standard.confirmTransfer(consignmentId, consignmentSummary, finalTransferForm, request.token.name)).uncache()
              }
            case _ =>
              throw new IllegalStateException(s"Unexpected Export status: $exportTransferStatus for consignment $consignmentId")
          }
        )
    }
  }

  def confirmTransfer(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    loadStandardPageBasedOnCtStatus(consignmentId, Ok)
  }

  def finalTransferConfirmationSubmit(consignmentId: UUID): Action[AnyContent] =
    standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      val errorFunction: Form[FinalTransferConfirmationData] => Future[Result] = { formWithErrors: Form[FinalTransferConfirmationData] =>
        loadStandardPageBasedOnCtStatus(consignmentId, BadRequest, formWithErrors)
      }

      val successFunction: FinalTransferConfirmationData => Future[Result] = { formData: FinalTransferConfirmationData =>
        val token: BearerAccessToken = request.token.bearerAccessToken

        for {
          consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
          exportStatus = consignmentStatusService.getStatusValues(consignmentStatuses, ExportType).values.headOption.flatten
          result <- exportStatus match {
            case Some(CompletedValue.value) => Future(Redirect(routes.TransferCompleteController.transferComplete(consignmentId)))
            case None =>
              for {
                _ <- confirmTransferService.addFinalTransferConfirmation(consignmentId, token, formData)
                _ <- consignmentExportService.updateTransferInitiated(consignmentId, token)
                _ <- consignmentExportService.triggerExport(consignmentId, token.toString)
              } yield Redirect(routes.TransferCompleteController.transferComplete(consignmentId))
            case _ =>
              throw new IllegalStateException(s"Unexpected Export status: $exportStatus for consignment $consignmentId")
          }
        } yield result
      }

      val formValidationResult: Form[FinalTransferConfirmationData] = finalTransferConfirmationForm.bindFromRequest()
      formValidationResult.fold(
        errorFunction,
        successFunction
      )
    }
}

case class ConsignmentSummaryData(seriesCode: String, transferringBody: String, totalFiles: Int, consignmentReference: String)

case class FinalTransferConfirmationData(transferLegalCustody: Boolean)
