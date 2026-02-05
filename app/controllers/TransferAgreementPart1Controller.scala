package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.Statuses._
import services.{ConsignmentMetadataService, ConsignmentService, ConsignmentStatusService, TransferAgreementService}
import uk.gov.nationalarchives.tdr.schema.generated.BaseSchema.legal_status
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
    val consignmentStatusService: ConsignmentStatusService,
    val consignmentMetadataService: ConsignmentMetadataService,
    val applicationConfig: ApplicationConfig
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  val transferAgreementForm: Form[TransferAgreementData] = Form(
    mapping(
      "publicRecord" -> boolean
        .verifying("All records must be confirmed as public before proceeding", b => b)
    )(publicRecord => TransferAgreementData(publicRecord, None))(data => Option(data.publicRecord))
  )

  private val transferAgreementFormNameAndLabel: Seq[(String, String)] = Seq(
    ("publicRecord", "Public Records")
  )

  private val legalStatusForm: Form[LegalStatusData] = Form(
    mapping(
      "legalStatus" -> nonEmptyText.verifying(_.nonEmpty)
    )(LegalStatusData.apply)(LegalStatusData.unapply)
  )
  private val legalStatusFormNameAndLabel: Seq[(String, String)] = Seq(
    ("legalStatus", "Public Record(s)"),
    ("legalStatus", "Welsh Public Record(s)"),
    ("legalStatus", "Not Public Record(s)")
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
      val fieldSetLegend = "I confirm that all records are:"
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
                  fieldSetLegend,
                  request.token.name
                )
              )
                .uncache()
            case None =>
              httpStatus(views.html.standard.transferAgreementPart1(consignmentId, reference, taForm, formAndLabel, warningMessage, fieldSetLegend, request.token.name))
                .uncache()
            case _ =>
              throw new IllegalStateException(s"Unexpected Transfer Agreement status: $transferAgreementStatus for consignment $consignmentId")
          }
        case _ => Redirect(routes.SeriesDetailsController.seriesDetails(consignmentId))
      }
    }
  }

  def transferAgreement(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    if (applicationConfig.blockLegalStatus) {
      loadStandardPageBasedOnTaStatus(consignmentId, Ok, transferAgreementForm)
    } else {
      for {
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
        statusesToValue = consignmentStatusService.getStatusValues(consignmentStatuses, SeriesType).values.headOption.flatten
      } yield {
        statusesToValue match {
          case Some(CompletedValue.value) =>
            Ok(views.html.standard.legalStatus(consignmentId, reference, legalStatusForm, legalStatusFormNameAndLabel, request.token.name)).uncache()
          case _ => Redirect(routes.SeriesDetailsController.seriesDetails(consignmentId))

        }
      }
    }
  }

  def transferAgreementSubmit(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    if (applicationConfig.blockLegalStatus) {
      submitTransferAgreement(consignmentId)
    } else {
      val errorFunction: Form[LegalStatusData] => Future[Result] = { formWithErrors: Form[LegalStatusData] =>
        for {
          reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        } yield BadRequest(views.html.standard.legalStatus(consignmentId, reference, formWithErrors, legalStatusFormNameAndLabel, request.token.name)).uncache()
      }
      val successFunction: LegalStatusData => Future[Result] = { formData: LegalStatusData =>
        for {
          _ <- consignmentMetadataService.addOrUpdateConsignmentMetadata(consignmentId, Map(legal_status -> formData.legalStatus), request.token.bearerAccessToken)
          consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, request.token.bearerAccessToken)
          statusesToValue = consignmentStatusService.getStatusValues(consignmentStatuses, TransferAgreementType).values.headOption.flatten
          result <- statusesToValue match {
            case Some(_) => Future(Redirect(routes.TransferAgreementPart2Controller.transferAgreement(consignmentId)))
            case None    =>
              consignmentStatusService.addConsignmentStatus(consignmentId, TransferAgreementType.id, InProgressValue.value, request.token.bearerAccessToken).map { _ =>
                Redirect(routes.TransferAgreementPart2Controller.transferAgreement(consignmentId))
              }
          }
        } yield result
      }
      val formValidationResult: Form[LegalStatusData] = legalStatusForm.bindFromRequest()

      formValidationResult.fold(
        errorFunction,
        successFunction
      )
    }
  }

  private def submitTransferAgreement(consignmentId: UUID)(implicit request: Request[AnyContent]): Future[Result] = {
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
          case None                        =>
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

case class TransferAgreementData(publicRecord: Boolean, english: Option[Boolean])
case class LegalStatusData(legalStatus: String)
