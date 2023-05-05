package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.Statuses.{CompletedValue, InProgressValue, SeriesType, TransferAgreementType}
import services.{ConsignmentService, ConsignmentStatusService, TransferAgreementService}
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TransferAgreementPart1Controller @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val transferAgreementService: TransferAgreementService,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  val transferAgreementForm: Form[TransferAgreementData] = Form(
    mapping(
      "publicRecord" -> boolean
        .verifying("All records must be confirmed as public before proceeding", b => b),
      "crownCopyright" -> boolean
        .verifying("All records must be confirmed Crown Copyright before proceeding", b => b)
    )((publicRecord, crownCopyright) => TransferAgreementData(publicRecord, crownCopyright, None))(data => Option(data.publicRecord, data.crownCopyright))
  )

  private val transferAgreementFormNameAndLabel: Seq[(String, String)] = Seq(
    ("publicRecord", "Public Records"),
    ("crownCopyright", "Crown Copyright")
  )

  private def loadStandardPageBasedOnTaStatus(consignmentId: UUID, httpStatus: Status, taForm: Form[TransferAgreementData])(implicit
      request: Request[AnyContent]
  ): Future[Result] = {
    for {
      consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
      statuses = consignmentStatusService.getStatusValues(consignmentStatuses, TransferAgreementType, SeriesType)
      transferAgreementStatus: Option[String] = statuses.get(TransferAgreementType).flatten
      seriesStatus: Option[String] = statuses.get(SeriesType).flatten
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield {
      val formAndLabel = transferAgreementFormNameAndLabel.filter(f => taForm.formats.keys.toList.contains(f._1))
      val warningMessage = Messages("transferAgreement.warning")
      val formHeading = "I confirm that all records are:"
      seriesStatus match {
        case Some(CompletedValue.value) =>
          transferAgreementStatus match {
            case Some(InProgressValue.value) | Some(CompletedValue.value) =>
              Ok(
                views.html.standard.transferAgreementPart1AlreadyConfirmed(
                  consignmentId,
                  reference,
                  transferAgreementForm,
                  formAndLabel,
                  warningMessage,
                  formHeading,
                  request.token.name
                )
              )
                .uncache()
            case None =>
              httpStatus(views.html.standard.transferAgreementPart1(consignmentId, reference, taForm, formAndLabel, warningMessage, formHeading, request.token.name))
                .uncache()
            case _ =>
              throw new IllegalStateException(s"Unexpected Transfer Agreement status: $transferAgreementStatus for consignment $consignmentId")
          }
        case _ => Redirect(routes.SeriesDetailsController.seriesDetails(consignmentId))
      }
    }
  }

  def transferAgreement(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    loadStandardPageBasedOnTaStatus(consignmentId, Ok, transferAgreementForm)
  }

  def transferAgreementSubmit(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val errorFunction: Form[TransferAgreementData] => Future[Result] = { formWithErrors: Form[TransferAgreementData] =>
      loadStandardPageBasedOnTaStatus(consignmentId, BadRequest, formWithErrors)
    }

    val successFunction: TransferAgreementData => Future[Result] = { formData: TransferAgreementData =>
      val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

      for {
        consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
        transferAgreementStatus = consignmentStatusService.getStatusValues(consignmentStatuses, TransferAgreementType).values.headOption.flatten
        result <- transferAgreementStatus match {
          case Some(InProgressValue.value) => Future(Redirect(routes.TransferAgreementPart2Controller.transferAgreement(consignmentId)))
          case None =>
            transferAgreementService
              .addTransferAgreementPart1(consignmentId, request.token.bearerAccessToken, formData)
              .map(_ => Redirect(routes.TransferAgreementPart2Controller.transferAgreement(consignmentId)))
          case _ =>
            throw new IllegalStateException(s"Unexpected Transfer Agreement status: $transferAgreementStatus for consignment $consignmentId")
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

case class TransferAgreementData(publicRecord: Boolean, crownCopyright: Boolean, english: Option[Boolean])
