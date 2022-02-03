package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, Lang, Langs, Messages}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.{ConsignmentService, ConsignmentStatusService, TransferAgreementService}
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TransferAgreementController1 @Inject()(val controllerComponents: SecurityComponents,
                                            val graphqlConfiguration: GraphQLConfiguration,
                                            val transferAgreementService: TransferAgreementService,
                                            val keycloakConfiguration: KeycloakConfiguration,
                                            val consignmentService: ConsignmentService,
                                            langs: Langs)
                                           (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {
  val transferAgreementForm: Form[TransferAgreementData] = Form(
    mapping(
      "publicRecord" -> boolean
        .verifying("All records must be confirmed as public before proceeding", b => b),
      "crownCopyright" -> boolean
        .verifying("All records must be confirmed Crown Copyright before proceeding", b => b),
      "english" -> boolean
        .verifying("All records must be confirmed as English language before proceeding", b => b)
    )(TransferAgreementData.apply)(TransferAgreementData.unapply)
  )

  private val options: Seq[(String, String)] = Seq("Yes" -> "true", "No" -> "false")
  implicit val language: Lang = langs.availables.head

  private def loadStandardPageBasedOnTaStatus(consignmentId: UUID, httpStatus: Status, taForm: Form[TransferAgreementData] = transferAgreementForm)
                                        (implicit request: Request[AnyContent]): Future[Result] = {
    val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

    consignmentStatusService.consignmentStatus(consignmentId, request.token.bearerAccessToken).map {
      consignmentStatus =>
        val transferAgreementStatus: Option[String] = consignmentStatus.flatMap(_.transferAgreement)
        val warningMessage = Messages("transferAgreement.warning")
        transferAgreementStatus match {
          case Some("InProgress") | Some("Completed") =>
            Ok(views.html.standard.transferAgreementAlreadyConfirmed(consignmentId, taForm, options, warningMessage)).uncache()
          case _ => httpStatus(views.html.standard.transferAgreement(consignmentId, taForm, options, warningMessage)).uncache()
        }
    }
  }

  def transferAgreement(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    loadStandardPageBasedOnTaStatus(consignmentId, Ok)
  }

  def judgmentTransferAgreement(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
      val warningMessage = Messages("judgmentTransferAgreement.warning")
      Future(Ok(views.html.judgment.judgmentTransferAgreement(consignmentId, warningMessage, request.token.name)).uncache())
  }

  def transferAgreementSubmit(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val errorFunction: Form[TransferAgreementData] => Future[Result] = { formWithErrors: Form[TransferAgreementData] =>
      loadStandardPageBasedOnTaStatus(consignmentId, BadRequest, formWithErrors)
    }

    val successFunction: TransferAgreementData => Future[Result] = { formData: TransferAgreementData =>
      val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

      for {
        consignmentStatus <- consignmentStatusService.consignmentStatus(consignmentId, request.token.bearerAccessToken)
        transferAgreementStatus = consignmentStatus.flatMap(_.transferAgreement)
        result <- transferAgreementStatus match {
          case Some("InProgress") => Future(Redirect(routes.TransferAgreementController2.transferAgreement(consignmentId)))
          case _ => transferAgreementService.addTransferAgreementNotCompliance(consignmentId, request.token.bearerAccessToken, formData)
            .map(_ => Redirect(routes.TransferAgreementController2.transferAgreement(consignmentId)))
        }
      } yield result
    }

    val formValidationResult: Form[TransferAgreementData] = transferAgreementForm.bindFromRequest()

    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }
}

case class TransferAgreementData(publicRecord: Boolean,
                                 crownCopyright: Boolean,
                                 english: Boolean)
