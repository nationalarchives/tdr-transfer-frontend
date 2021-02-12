package controllers

import java.util.UUID

import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.AddFinalTransferConfirmation.AddFinalTransferConfirmation
import graphql.codegen.types.AddFinalTransferConfirmationInput
import javax.inject.Inject
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping}
import play.api.i18n.{I18nSupport, Lang, Langs}
import play.api.mvc.{Action, AnyContent, Request, RequestHeader, Result}
import services.ApiErrorHandling.sendApiRequest
import services.ConsignmentService
import services.ConsignmentExportService
import uk.gov.nationalarchives.tdr.GraphQLClient
import validation.ValidatedActions

import scala.concurrent.{ExecutionContext, Future}

class TransferSummaryController @Inject()(val controllerComponents: SecurityComponents,
                                          val graphqlConfiguration: GraphQLConfiguration,
                                          val keycloakConfiguration: KeycloakConfiguration,
                                          consignmentService: ConsignmentService,
                                          val consignmentExportService: ConsignmentExportService,
                                          langs: Langs)
                                         (implicit val ec: ExecutionContext) extends ValidatedActions with I18nSupport {

  private val addFinalTransferConfirmationClient: GraphQLClient[AddFinalTransferConfirmation.Data, AddFinalTransferConfirmation.Variables] =
    graphqlConfiguration.getClient[AddFinalTransferConfirmation.Data, AddFinalTransferConfirmation.Variables]()
  implicit val language: Lang = langs.availables.head
  val finalTransferConfirmationForm: Form[FinalTransferConfirmationData] = Form(
    mapping(
      "openRecords" -> boolean
        .verifying(messagesApi("transferSummary.openRecords.error"), b => b),
      "transferLegalOwnership" -> boolean
        .verifying(messagesApi("transferSummary.transferLegalOwnership.error"), b => b)
    )(FinalTransferConfirmationData.apply)(FinalTransferConfirmationData.unapply)
  )

  private def getConsignmentSummary(request: Request[AnyContent], consignmentId: UUID)
                                   (implicit requestHeader: RequestHeader): Future[ConsignmentSummaryData] = {
    consignmentService.getConsignmentTransferSummary(consignmentId, request.token.bearerAccessToken)
      .map { summary =>
        ConsignmentSummaryData(summary.series.get.code,
          summary.transferringBody.get.name,
          summary.totalFiles)
      }
  }

  def transferSummary(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    getConsignmentSummary(request, consignmentId)
      .map { consignmentSummary =>
        Ok(views.html.transferSummary(consignmentId, consignmentSummary, finalTransferConfirmationForm))
      }
  }

  def transferSummarySubmit(consignmentId: UUID): Action[AnyContent] =
    secureAction.async { implicit request: Request[AnyContent] =>
      val errorFunction: Form[FinalTransferConfirmationData] => Future[Result] = { formWithErrors: Form[FinalTransferConfirmationData] =>
        getConsignmentSummary(request, consignmentId).map { summary =>
          BadRequest(views.html.transferSummary(consignmentId, summary, formWithErrors))
        }
      }

      val successFunction: FinalTransferConfirmationData => Future[Result] = { formData: FinalTransferConfirmationData =>
        val addFinalTransferConfirmationInput: AddFinalTransferConfirmationInput = AddFinalTransferConfirmationInput(consignmentId,
          formData.openRecords,
          formData.transferLegalOwnership)

        val variables: AddFinalTransferConfirmation.Variables = AddFinalTransferConfirmation.Variables(addFinalTransferConfirmationInput)

        sendApiRequest(addFinalTransferConfirmationClient, AddFinalTransferConfirmation.document, request.token.bearerAccessToken, variables)

        for {
          _ <- consignmentExportService.updateTransferInititated(consignmentId, request.token.bearerAccessToken)
          _ <- consignmentExportService.triggerExport(consignmentId, request.token.bearerAccessToken.toString)
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

case class ConsignmentSummaryData(seriesCode: Option[String],
                                  transferringBody: Option[String],
                                  totalFiles: Int)

case class FinalTransferConfirmationData(openRecords: Boolean,
                                         transferLegalOwnership: Boolean)
