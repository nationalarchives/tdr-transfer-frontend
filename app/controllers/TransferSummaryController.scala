package controllers

import java.util.UUID

import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import javax.inject.Inject
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms.{boolean, mapping}
import play.api.i18n.{I18nSupport, Lang, Langs}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.ConsignmentExportService
import validation.ValidatedActions

import scala.concurrent.{ExecutionContext, Future}

class TransferSummaryController @Inject()(val controllerComponents: SecurityComponents,
                                          val graphqlConfiguration: GraphQLConfiguration,
                                          val keycloakConfiguration: KeycloakConfiguration,
                                          val consignmentExportService: ConsignmentExportService,
                                          langs: Langs)
                                         (implicit val ec: ExecutionContext) extends ValidatedActions with I18nSupport {

  implicit val language: Lang = langs.availables.head
  val transferSummaryForm: Form[TransferSummaryData] = Form(
    mapping(
      "openRecords" -> boolean
        .verifying(messagesApi("transferSummary.openRecords.error"), b => b),
      "transferLegalOwnership" -> boolean
        .verifying(messagesApi("transferSummary.transferLegalOwnership.error"), b => b)
    )(TransferSummaryData.apply)(TransferSummaryData.unapply)
  )

  def transferSummary(consignmentId: UUID): Action[AnyContent] = consignmentExists(consignmentId) { implicit request: Request[AnyContent] =>
    Ok(views.html.transferSummary(consignmentId, transferSummaryForm))
  }

  def transferSummarySubmit(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    val errorFunction: Form[TransferSummaryData] => Future[Result] = { formWithErrors: Form[TransferSummaryData] =>
      Future.successful(BadRequest(views.html.transferSummary(consignmentId, formWithErrors)))
    }

    val successFunction: TransferSummaryData => Future[Result] = { _: TransferSummaryData =>
      for {
        exportTriggered <- consignmentExportService.triggerExport(consignmentId, request.token.bearerAccessToken.toString)
        res <- Future(Redirect(routes.TransferConfirmationController.transferConfirmation(consignmentId, exportTriggered)))
      } yield res
    }

    val formValidationResult: Form[TransferSummaryData] = transferSummaryForm.bindFromRequest()
    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }
}

case class TransferSummaryData(openRecords: Boolean,
                               transferLegalOwnership: Boolean)
