package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.Statuses.{CompletedValue, InProgressValue, SeriesType, TransferAgreementType}
import services.{ConsignmentService, ConsignmentStatusService, TransferAgreementService}
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TransferAgreementComplianceController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val transferAgreementService: TransferAgreementService,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService,
    val applicationConfig: ApplicationConfig
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {
  val transferAgreementFormWithOpenRecords: Form[TransferAgreementComplianceData] = Form(
    mapping(
      "droAppraisalSelection" -> boolean
        .verifying("Departmental Records Officer (DRO) must have signed off the appraisal and selection decision for records", b => b),
      "droSensitivity" -> boolean
        .verifying("Departmental Records Officer (DRO) must have signed off sensitivity review", b => b),
      "openRecords" -> boolean
        .verifying("All records must be open", b => b)
    )((droAppraisalSelection, droSensitivity, openRecords) => TransferAgreementComplianceData(droAppraisalSelection, droSensitivity, Option(openRecords)))(data =>
      Option(data.droAppraisalSelection, data.droSensitivity, data.openRecords.getOrElse(false))
    )
  )

  val transferAgreementForm: Form[TransferAgreementComplianceData] = Form(
    mapping(
      "droAppraisalSelection" -> boolean
        .verifying("Departmental Records Officer (DRO) must have signed off the appraisal and selection decision for records", b => b),
      "droSensitivity" -> boolean
        .verifying("Departmental Records Officer (DRO) must have signed off sensitivity review", b => b)
    )((droAppraisalSelection, droSensitivity) => TransferAgreementComplianceData(droAppraisalSelection, droSensitivity, None))(data =>
      Option(data.droSensitivity, data.droAppraisalSelection)
    )
  )

  private def form: Form[TransferAgreementComplianceData] =
    if (applicationConfig.blockClosureMetadata && applicationConfig.blockDescriptiveMetadata) {
      transferAgreementFormWithOpenRecords
    } else {
      transferAgreementForm
    }

  val taFormNamesAndLabels: Seq[(String, String)] = Seq(
    ("droAppraisalSelection", "I confirm that the Departmental Records Officer (DRO) has signed off on the appraisal and selection decision."),
    ("droSensitivity", "I confirm that the Departmental Records Officer (DRO) has signed off on the sensitivity review."),
    ("openRecords", "I confirm that all records are open and no Freedom of Information (FOI) exemptions apply to these records.")
  )

  private def loadStandardPageBasedOnTaStatus(consignmentId: UUID, httpStatus: Status, taForm: Form[TransferAgreementComplianceData])(implicit
      request: Request[AnyContent]
  ): Future[Result] = {
    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
      statuses = consignmentStatusService.getStatusValues(consignmentStatuses, TransferAgreementType, SeriesType)
      transferAgreementStatus: Option[String] = statuses.get(TransferAgreementType).flatten
      seriesStatus: Option[String] = statuses.get(SeriesType).flatten
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield {
      val formAndLabels = taFormNamesAndLabels.filter(f => taForm.formats.keys.toList.contains(f._1))
      seriesStatus match {
        case Some(CompletedValue.value) =>
          transferAgreementStatus match {
            case Some(CompletedValue.value) =>
              Ok(
                views.html.standard
                  .transferAgreementComplianceAlreadyConfirmed(consignmentId, reference, form, formAndLabels, request.token.name)
              )
                .uncache()
            case Some(InProgressValue.value) =>
              httpStatus(views.html.standard.transferAgreementCompliance(consignmentId, reference, taForm, formAndLabels, request.token.name)).uncache()
            case None =>
              Redirect(routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId)).uncache()
            case _ =>
              throw new IllegalStateException(s"Unexpected Transfer Agreement status: $transferAgreementStatus for consignment $consignmentId")
          }
        case _ => Redirect(routes.SeriesDetailsController.seriesDetails(consignmentId))
      }
    }
  }

  def transferAgreement(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    loadStandardPageBasedOnTaStatus(consignmentId, Ok, form)
  }

  def transferAgreementSubmit(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val errorFunction: Form[TransferAgreementComplianceData] => Future[Result] = { formWithErrors: Form[TransferAgreementComplianceData] =>
      loadStandardPageBasedOnTaStatus(consignmentId, BadRequest, formWithErrors)
    }

    val successFunction: TransferAgreementComplianceData => Future[Result] = { formData: TransferAgreementComplianceData =>
      val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

      for {
        consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
        transferAgreementStatus = consignmentStatusService.getStatusValues(consignmentStatuses, TransferAgreementType).values.headOption.flatten
        result <- transferAgreementStatus match {
          case Some(CompletedValue.value) => Future(Redirect(routes.UploadController.uploadPage(consignmentId)))
          case Some(InProgressValue.value) =>
            transferAgreementService
              .addTransferAgreementCompliance(consignmentId, request.token.bearerAccessToken, formData)
              .map(_ => Redirect(routes.UploadController.uploadPage(consignmentId)))
          case _ =>
            throw new IllegalStateException(s"Unexpected Transfer Agreement status: $transferAgreementStatus for consignment $consignmentId")
        }
      } yield result
    }

    val formValidationResult: Form[TransferAgreementComplianceData] = form.bindFromRequest()

    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }
}

case class TransferAgreementComplianceData(droAppraisalSelection: Boolean, droSensitivity: Boolean, openRecords: Option[Boolean])
