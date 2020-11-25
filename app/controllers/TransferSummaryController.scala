package controllers

import java.util.UUID

import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import javax.inject.Inject
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping}
import play.api.i18n.{I18nSupport, Lang, Langs}
import play.api.mvc.{Action, AnyContent, Request, RequestHeader, Result}
import services.ConsignmentService
import validation.ValidatedActions

import scala.concurrent.{ExecutionContext, Future}

class TransferSummaryController @Inject()(val controllerComponents: SecurityComponents,
                                          val graphqlConfiguration: GraphQLConfiguration,
                                          val keycloakConfiguration: KeycloakConfiguration,
                                          consignmentService: ConsignmentService,
                                          langs: Langs)
                                         (implicit val ec: ExecutionContext) extends ValidatedActions with I18nSupport {

  implicit val language: Lang = langs.availables.head
  val transferConfirmationForm: Form[TransferConfirmationData] = Form(
    mapping(
      "openRecords" -> boolean
        .verifying(messagesApi("transferSummary.openRecords.error"), b => b),
      "transferLegalOwnership" -> boolean
        .verifying(messagesApi("transferSummary.transferLegalOwnership.error"), b => b)
    )(TransferConfirmationData.apply)(TransferConfirmationData.unapply)
  )

  private def getConsignmentSummary(request: Request[AnyContent], consignmentId: UUID)
                                      (implicit requestHeader: RequestHeader): Future[ConsignmentSummaryData] = {
    consignmentService.getConsignmentTransferSummary(consignmentId, request.token.bearerAccessToken)
      .map{summary =>
        ConsignmentSummaryData(summary.series.get.code,
                              summary.transferringBody.get.name,
                              summary.totalFiles)
      }
  }

  def transferSummary(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    getConsignmentSummary(request, consignmentId)
      .map{ consignmentSummary =>
        Ok(views.html.transferSummary(consignmentId, consignmentSummary, transferConfirmationForm))
      }
  }

  def transferSummarySubmit(consignmentId: UUID): Action[AnyContent] =
    secureAction.async { implicit request: Request[AnyContent] =>
    val errorFunction: Form[TransferConfirmationData] => Future[Result] = { formWithErrors: Form[TransferConfirmationData] =>
      getConsignmentSummary(request, consignmentId).map{summary =>
        BadRequest(views.html.transferSummary(consignmentId, summary, formWithErrors))
      }
    }

    val successFunction: TransferConfirmationData => Future[Result] = { formData: TransferConfirmationData =>
      Future(Ok(views.html.transferConfirmation()))
      //Code here will be replaced with a mutation to database
    }

    val formValidationResult: Form[TransferConfirmationData] = transferConfirmationForm.bindFromRequest()
    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }
}

case class ConsignmentSummaryData(seriesCode: Option[String],
                                  transferringBody: Option[String],
                                  totalFiles: Int)

case class TransferConfirmationData(openRecords: Boolean,
                               transferLegalOwnership: Boolean)
