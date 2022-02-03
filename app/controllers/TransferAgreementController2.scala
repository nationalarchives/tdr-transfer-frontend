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
class TransferAgreementController2 @Inject()(val controllerComponents: SecurityComponents,
                                            val graphqlConfiguration: GraphQLConfiguration,
                                            val transferAgreementService: TransferAgreementService,
                                            val keycloakConfiguration: KeycloakConfiguration,
                                            val consignmentService: ConsignmentService,
                                            langs: Langs)
                                           (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {
  val transferAgreementForm: Form[TransferAgreementComplianceData] = Form(
    mapping(
      "droAppraisalSelection" -> boolean
        .verifying("Departmental Records Officer (DRO) must have signed off the appraisal and selection decision for records", b => b),
      "droSensitivity" -> boolean
        .verifying("Departmental Records Officer (DRO) must have signed off sensitivity review", b => b),
      "openRecords" -> boolean
        .verifying("All records must be open", b => b)
    )(TransferAgreementComplianceData.apply)(TransferAgreementComplianceData.unapply)
  )

  private val options: Seq[(String, String)] = Seq("Yes" -> "true", "No" -> "false")
  implicit val language: Lang = langs.availables.head

  private def loadStandardPageBasedOnTaStatus(consignmentId: UUID, httpStatus: Status, taForm: Form[TransferAgreementComplianceData] = transferAgreementForm)
                                        (implicit request: Request[AnyContent]): Future[Result] = {
    val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

    consignmentStatusService.consignmentStatus(consignmentId, request.token.bearerAccessToken).map {
      consignmentStatus =>
        val transferAgreementStatus: Option[String] = consignmentStatus.flatMap(_.transferAgreement)
        val warningMessage = Messages("transferAgreement.warning")
        transferAgreementStatus match {
          case Some("Completed") =>
            Ok(views.html.standard.transferAgreementAlreadyConfirmed2(consignmentId, taForm, options, warningMessage, request.token.name)).uncache()
          case Some("InProgress") =>
            httpStatus(views.html.standard.transferAgreement2(consignmentId, taForm, options, warningMessage, request.token.name)).uncache()
          case _ =>
            Redirect(routes.TransferAgreementController1.transferAgreement(consignmentId)).uncache()
        }
    }
  }

  def transferAgreement(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    loadStandardPageBasedOnTaStatus(consignmentId, Ok)
  }

  def transferAgreementSubmit(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val errorFunction: Form[TransferAgreementComplianceData] => Future[Result] = { formWithErrors: Form[TransferAgreementComplianceData] =>
      loadStandardPageBasedOnTaStatus(consignmentId, BadRequest, formWithErrors)
    }

    val successFunction: TransferAgreementComplianceData => Future[Result] = { formData: TransferAgreementComplianceData =>
      val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

      for {
        consignmentStatus <- consignmentStatusService.consignmentStatus(consignmentId, request.token.bearerAccessToken)
        transferAgreementStatus = consignmentStatus.flatMap(_.transferAgreement)
        result <- transferAgreementStatus match {
          case Some("Completed") => Future(Redirect(routes.UploadController.uploadPage(consignmentId)))
          case _ => transferAgreementService.addTransferAgreementCompliance(consignmentId, request.token.bearerAccessToken, formData)
            .map(_ => Redirect(routes.UploadController.uploadPage(consignmentId)))
        }
      } yield result
    }

    val formValidationResult: Form[TransferAgreementComplianceData] = transferAgreementForm.bindFromRequest()

    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }
}

case class TransferAgreementComplianceData(droAppraisalSelection: Boolean,
                                           droSensitivity: Boolean,
                                           openRecords: Boolean)
