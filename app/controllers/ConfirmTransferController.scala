package controllers

import auth.TokenSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping}
import play.api.i18n.{I18nSupport, Lang, Langs}
import play.api.mvc._
import services.{ConfirmTransferService, ConsignmentExportService, ConsignmentService, ConsignmentStatusService}
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
      "openRecords" -> boolean
        .verifying("All records must be confirmed as open before proceeding", b => b),
      "transferLegalCustody" -> boolean
        .verifying("Transferral of legal custody of all records must be confirmed before proceeding", b => b)
    )(FinalTransferConfirmationData.apply)(FinalTransferConfirmationData.unapply)
  )

  private def getConsignmentSummary(request: Request[AnyContent], consignmentId: UUID)(implicit requestHeader: RequestHeader): Future[ConsignmentSummaryData] = {
    consignmentService
      .getConsignmentConfirmTransfer(consignmentId, request.token.bearerAccessToken)
      .map { summary =>
        ConsignmentSummaryData(summary.series.get.code, summary.transferringBody.get.name, summary.totalFiles, summary.consignmentReference)
      }
  }

  private def loadStandardPageBasedOnCtStatus(consignmentId: UUID, httpStatus: Status, finalTransferForm: Form[FinalTransferConfirmationData] = finalTransferConfirmationForm)(
      implicit request: Request[AnyContent]
  ): Future[Result] = {
    consignmentStatusService.getConsignmentStatus(consignmentId, request.token.bearerAccessToken).flatMap { consignmentStatus =>
      val exportTransferStatus = consignmentStatus.flatMap(_.export)
      exportTransferStatus match {
        case Some("InProgress") | Some("Completed") | Some("Failed") =>
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
    }
  }

  def confirmTransfer(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    loadStandardPageBasedOnCtStatus(consignmentId, Ok)
  }

  def finalTransferConfirmationSubmit(consignmentId: UUID): Action[AnyContent] =
    standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      val errorFunction: Form[FinalTransferConfirmationData] => Future[Result] = { formWithErrors: Form[FinalTransferConfirmationData] =>
        loadStandardPageBasedOnCtStatus(consignmentId, BadRequest, formWithErrors)
      }

      val successFunction: FinalTransferConfirmationData => Future[Result] = { formData: FinalTransferConfirmationData =>
        val token: BearerAccessToken = request.token.bearerAccessToken

        for {
          consignmentStatus <- consignmentStatusService.getConsignmentStatus(consignmentId, request.token.bearerAccessToken)
          exportStatus = consignmentStatus.flatMap(_.export)
          result <- exportStatus match {
            case Some("Completed") => Future(Redirect(routes.TransferCompleteController.transferComplete(consignmentId)))
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

  def finalJudgmentTransferConfirmationSubmit(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignmentStatus <- consignmentStatusService.getConsignmentStatus(consignmentId, request.token.bearerAccessToken)
      exportStatus = consignmentStatus.flatMap(_.export)
      res <- {
        exportStatus match {
          case Some("InProgress") | Some("Completed") | Some("Failed") =>
            consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken).map { consignmentRef =>
              Ok(views.html.transferAlreadyCompleted(consignmentId, consignmentRef, request.token.name, isJudgmentUser = true)).uncache()
            }
          case None =>
            val token: BearerAccessToken = request.token.bearerAccessToken
            for {
              _ <- confirmTransferService.addFinalJudgmentTransferConfirmation(consignmentId, token)
              _ <- consignmentExportService.updateTransferInitiated(consignmentId, request.token.bearerAccessToken)
              _ <- consignmentExportService.triggerExport(consignmentId, request.token.bearerAccessToken.toString)
              res <- Future(Redirect(routes.TransferCompleteController.judgmentTransferComplete(consignmentId)))
            } yield res
          case _ =>
            throw new IllegalStateException(s"Unexpected Export status: $exportStatus for consignment $consignmentId")
        }
      }
    } yield res
  }
}

case class ConsignmentSummaryData(seriesCode: String, transferringBody: String, totalFiles: Int, consignmentReference: String)

case class FinalTransferConfirmationData(openRecords: Boolean, transferLegalCustody: Boolean)
