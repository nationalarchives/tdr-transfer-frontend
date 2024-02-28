package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.Statuses.InProgressValue
import services._
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AdditionalMetadataEntryMethodController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentStatusService: ConsignmentStatusService,
    val consignmentService: ConsignmentService,
    val applicationConfig: ApplicationConfig
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  val additionalMetadataEntryForm: Form[AdditionalMetadataEntryData] = Form(
    mapping(
      "metadataRoute" -> optional(text).verifying("Please choose an option", t => t.nonEmpty)
    )(AdditionalMetadataEntryData.apply)(AdditionalMetadataEntryData.unapply)
  )

  def additionalMetadataEntryMethodPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    if (applicationConfig.blockDraftMetadataUpload) {
      Future(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
    } else {
      for {
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      } yield {
        Ok(views.html.standard.additionalMetadataEntryMethod(consignmentId, reference, additionalMetadataEntryForm, request.token.name))
          .uncache()
      }
    }
  }

  def submitAdditionalMetadataEntryMethod(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    if (applicationConfig.blockDraftMetadataUpload) {
      Future(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
    } else {
      val formValidationResult: Form[AdditionalMetadataEntryData] = additionalMetadataEntryForm.bindFromRequest()

      val errorFunction: Form[AdditionalMetadataEntryData] => Future[Result] = { formWithErrors: Form[AdditionalMetadataEntryData] =>
        for {
          reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        } yield {
          Ok(views.html.standard.additionalMetadataEntryMethod(consignmentId, reference, formWithErrors, request.token.name))
            .uncache()
        }
      }

      val successFunction: AdditionalMetadataEntryData => Future[Result] = { formData: AdditionalMetadataEntryData =>
        formData.metadataRoute match {
          case Some("manual") => Future.successful(Redirect(routes.AdditionalMetadataController.start(consignmentId)))
          case Some("csv") =>
            consignmentStatusService
              .addConsignmentStatus(consignmentId, "DraftMetadata", InProgressValue.value, request.token.bearerAccessToken)
              .map(_ => Redirect(routes.DraftMetadataUploadController.draftMetadataUploadPage(consignmentId)))
          case _ => Future.successful(Redirect(routes.DownloadMetadataController.downloadMetadataPage(consignmentId)))
        }
      }

      formValidationResult.fold(
        errorFunction,
        successFunction
      )
    }
  }
}

case class AdditionalMetadataEntryData(metadataRoute: Option[String])
