package controllers

import auth.TokenSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken

import java.util.UUID
import configuration.{GraphQLConfiguration, KeycloakConfiguration}

import javax.inject.Inject
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping}
import play.api.i18n.{I18nSupport, Lang, Langs}
import play.api.mvc.{Action, AnyContent, Request, RequestHeader, Result}
import services.ApiErrorHandling.sendApiRequest
import services.{ConfirmTransferService, ConsignmentExportService, ConsignmentService}
import uk.gov.nationalarchives.tdr.GraphQLClient

import scala.concurrent.{ExecutionContext, Future}

class ConfirmTransferController @Inject()(val controllerComponents: SecurityComponents,
                                          val graphqlConfiguration: GraphQLConfiguration,
                                          val keycloakConfiguration: KeycloakConfiguration,
                                          consignmentService: ConsignmentService,
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

  def confirmTransfer(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    getConsignmentSummary(request, consignmentId)
      .map { consignmentSummary =>
        Ok(views.html.standard.confirmTransfer(consignmentId, consignmentSummary, finalTransferConfirmationForm))
      }
  }

  def finalTransferConfirmationSubmit(consignmentId: UUID): Action[AnyContent] =
    secureAction.async { implicit request: Request[AnyContent] =>
      val errorFunction: Form[FinalTransferConfirmationData] => Future[Result] = { formWithErrors: Form[FinalTransferConfirmationData] =>
        getConsignmentSummary(request, consignmentId).map { summary =>
          BadRequest(views.html.standard.confirmTransfer(consignmentId, summary, formWithErrors))
        }
      }

      val successFunction: FinalTransferConfirmationData => Future[Result] = { formData: FinalTransferConfirmationData =>
        val token: BearerAccessToken = request.token.bearerAccessToken

        for {
          _ <- confirmTransferService.addFinalTransferConfirmation(consignmentId, token, formData)
          _ <- consignmentExportService.updateTransferInititated(consignmentId, token)
          _ <- consignmentExportService.triggerExport(consignmentId, token.toString)
          res <- Future(Redirect(routes.TransferCompleteController.transferComplete(consignmentId)))
        } yield res
      }

      val formValidationResult: Form[FinalTransferConfirmationData] = finalTransferConfirmationForm.bindFromRequest()
      formValidationResult.fold(
        errorFunction,
        successFunction
      )
    }
}

case class ConsignmentSummaryData(seriesCode: String,
                                  transferringBody: String,
                                  totalFiles: Int,
                                  consignmentReference: String)

case class FinalTransferConfirmationData(openRecords: Boolean,
                                         transferLegalOwnership: Boolean)
