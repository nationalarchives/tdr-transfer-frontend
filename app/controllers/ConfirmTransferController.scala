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

class ConfirmTransferController @Inject()(val controllerComponents: SecurityComponents,
                                          val graphqlConfiguration: GraphQLConfiguration,
                                          val keycloakConfiguration: KeycloakConfiguration,
                                          val consignmentService: ConsignmentService,
                                          val confirmTransferService: ConfirmTransferService,
                                          val consignmentExportService: ConsignmentExportService,
                                          langs: Langs)
                                         (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {


  implicit val language: Lang = langs.availables.head
  val finalTransferConfirmationForm: Form[FinalTransferConfirmationData] = Form(
    mapping(
      "openRecords" -> boolean
        .verifying("All records must be confirmed as open before proceeding", b => b),
      "transferLegalOwnership" -> boolean
        .verifying("Transferral of legal ownership of all records must be confirmed before proceeding", b => b)
    )(FinalTransferConfirmationData.apply)(FinalTransferConfirmationData.unapply)
  )

  private def getConsignmentSummary(request: Request[AnyContent], consignmentId: UUID)
                                   (implicit requestHeader: RequestHeader): Future[ConsignmentSummaryData] = {
    consignmentService.getConsignmentConfirmTransfer(consignmentId, request.token.bearerAccessToken)
      .map { summary =>
        ConsignmentSummaryData(summary.series.get.code,
          summary.transferringBody.get.name,
          summary.totalFiles,
          summary.consignmentReference)
      }
  }

  private def loadStandardPageBasedOnCtStatus(consignmentId: UUID, httpStatus: Status,
                                              finalTransferForm: Form[FinalTransferConfirmationData] = finalTransferConfirmationForm)
                                             (implicit request: Request[AnyContent]): Future[Result] = {
    val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

    consignmentStatusService.consignmentStatus(consignmentId, request.token.bearerAccessToken).flatMap {
      consignmentStatus =>
        val confirmTransferStatus = consignmentStatus.flatMap(_.confirmTransfer)
        confirmTransferStatus match {
          case Some("Completed") => Future(Ok(views.html.standard.confirmTransferAlreadyConfirmed(consignmentId, request.token.name)).uncache())
          case _ =>
            getConsignmentSummary(request, consignmentId)
              .map { consignmentSummary =>
                httpStatus(views.html.standard.confirmTransfer(consignmentId, consignmentSummary, finalTransferForm, request.token.name)).uncache()
              }
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
        val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

        for {
          consignmentStatus <- consignmentStatusService.consignmentStatus(consignmentId, request.token.bearerAccessToken)
          confirmTransferStatus = consignmentStatus.flatMap(_.confirmTransfer)
          result <- confirmTransferStatus match {
            case Some("Completed") => Future(Redirect(routes.TransferCompleteController.transferComplete(consignmentId)))
            case _ =>
              confirmTransferService.addFinalTransferConfirmation(consignmentId, token, formData)
              consignmentExportService.updateTransferInititated(consignmentId, token)
              consignmentExportService.triggerExport(consignmentId, token.toString)
              Future(Redirect(routes.TransferCompleteController.transferComplete(consignmentId)))
          }
        } yield result
      }

      val formValidationResult: Form[FinalTransferConfirmationData] = finalTransferConfirmationForm.bindFromRequest()
      formValidationResult.fold(
        errorFunction,
        successFunction
      )
    }

  def finalJudgmentTransferConfirmationSubmit(consignmentId: UUID): Action[AnyContent] =
    judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      val token: BearerAccessToken = request.token.bearerAccessToken

      for {
        _ <- confirmTransferService.addFinalJudgmentTransferConfirmation(consignmentId, token)
        _ <- consignmentExportService.updateTransferInititated(consignmentId, request.token.bearerAccessToken)
        _ <- consignmentExportService.triggerExport(consignmentId, request.token.bearerAccessToken.toString)
        res <- Future(Redirect(routes.TransferCompleteController.judgmentTransferComplete(consignmentId)))
      } yield res
    }
}

case class ConsignmentSummaryData(seriesCode: String,
                                  transferringBody: String,
                                  totalFiles: Int,
                                  consignmentReference: String)

case class FinalTransferConfirmationData(openRecords: Boolean,
                                         transferLegalOwnership: Boolean)
