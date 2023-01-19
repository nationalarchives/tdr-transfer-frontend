package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, Messages}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.{ConsignmentService, ConsignmentStatusService, TransferAgreementService}
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class TransferAgreementPrivateBetaController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val transferAgreementService: TransferAgreementService,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService,
    val configuration: Configuration
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  val blockClosureMetadata: Boolean = configuration.get[Boolean]("featureAccessBlock.closureMetadata")

  private val transferAgreementFormWithEnglish: Form[TransferAgreementData] = Form(
    mapping(
      "publicRecord" -> boolean
        .verifying("All records must be confirmed as public before proceeding", b => b),
      "crownCopyright" -> boolean
        .verifying("All records must be confirmed Crown Copyright before proceeding", b => b),
      "english" -> boolean
        .verifying("All records must be confirmed as English language before proceeding", b => b)
    )((publicRecord, crownCopyright, english) => TransferAgreementData(publicRecord, crownCopyright, Option(english)))(data => Option(data.publicRecord, data.crownCopyright, data.english.getOrElse(false)))
  )

  val transferAgreementForm: Form[TransferAgreementData] = Form(
    mapping(
      "publicRecord" -> boolean
        .verifying("All records must be confirmed as public before proceeding", b => b),
      "crownCopyright" -> boolean
        .verifying("All records must be confirmed Crown Copyright before proceeding", b => b),
    )((publicRecord, crownCopyright) => TransferAgreementData(publicRecord, crownCopyright, None))(data => Option(data.publicRecord, data.crownCopyright))
  )

  private val transferAgreementFormNameAndLabel: Seq[(String, String)] = Seq(
    ("publicRecord", "I confirm that the records are Public Records."),
    ("crownCopyright", "I confirm that the records are all Crown Copyright."),
    ("english", "I confirm that the records are all in English.")
  )


  private def loadStandardPageBasedOnTaStatus(consignmentId: UUID, httpStatus: Status, taForm: Form[TransferAgreementData])(implicit
      request: Request[AnyContent]
  ): Future[Result] = {
    for {
      consignmentStatus <- consignmentStatusService.getConsignmentStatus(consignmentId, request.token.bearerAccessToken)
      transferAgreementStatus: Option[String] = consignmentStatus.flatMap(_.transferAgreement)
      seriesStatus: Option[String] = consignmentStatus.flatMap(_.series)
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield {
      val formAndLabel = transferAgreementFormNameAndLabel.filter(f => taForm.formats.keys.toList.contains(f._1))
      val warningMessage = Messages("transferAgreement.warning")
      seriesStatus match {
        case Some("Completed") =>
          transferAgreementStatus match {
            case Some("InProgress") | Some("Completed") =>
              Ok(
                views.html.standard.transferAgreementPrivateBetaAlreadyConfirmed(
                  consignmentId,
                  reference,
                  taForm,
                  formAndLabel,
                  warningMessage,
                  request.token.name
                )
              )
                .uncache()
            case None =>
              httpStatus(views.html.standard.transferAgreementPrivateBeta(consignmentId, reference, taForm, formAndLabel, warningMessage, request.token.name))
                .uncache()
            case _ =>
              throw new IllegalStateException(s"Unexpected Transfer Agreement status: $transferAgreementStatus for consignment $consignmentId")
          }
        case _ => Redirect(routes.SeriesDetailsController.seriesDetails(consignmentId))
      }
    }
  }

  def transferAgreement(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val form = if(blockClosureMetadata) {
      transferAgreementFormWithEnglish
    } else {
      transferAgreementForm
    }
    loadStandardPageBasedOnTaStatus(consignmentId, Ok, form)
  }

  def transferAgreementSubmit(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val errorFunction: Form[TransferAgreementData] => Future[Result] = { formWithErrors: Form[TransferAgreementData] =>
      loadStandardPageBasedOnTaStatus(consignmentId, BadRequest, formWithErrors)
    }

    val successFunction: TransferAgreementData => Future[Result] = { formData: TransferAgreementData =>
      val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

      for {
        consignmentStatus <- consignmentStatusService.getConsignmentStatus(consignmentId, request.token.bearerAccessToken)
        transferAgreementStatus = consignmentStatus.flatMap(_.transferAgreement)
        result <- transferAgreementStatus match {
          case Some("InProgress") => Future(Redirect(routes.TransferAgreementComplianceController.transferAgreement(consignmentId)))
          case None =>
            transferAgreementService
              .addTransferAgreementPrivateBeta(consignmentId, request.token.bearerAccessToken, formData)
              .map(_ => Redirect(routes.TransferAgreementComplianceController.transferAgreement(consignmentId)))
          case _ =>
            throw new IllegalStateException(s"Unexpected Transfer Agreement status: $transferAgreementStatus for consignment $consignmentId")
        }
      } yield result
    }

    val formValidationResult: Form[TransferAgreementData] = transferAgreementFormWithEnglish.bindFromRequest()

    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }
}

case class TransferAgreementData(publicRecord: Boolean, crownCopyright: Boolean, english: Option[Boolean])
