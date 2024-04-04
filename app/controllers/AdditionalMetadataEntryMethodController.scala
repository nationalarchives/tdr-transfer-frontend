package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, KeycloakConfiguration}
import graphql.codegen.types.ConsignmentStatusInput
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms.{mapping, optional, text}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.Statuses.{DraftMetadataType, InProgressValue}
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
      "metadataRoute" -> optional(text).verifying("Choose a way of entering metadata", t => t.nonEmpty)
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
    val token = request.token.bearerAccessToken
    if (applicationConfig.blockDraftMetadataUpload) {
      Future(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = false)))
    } else {
      val formValidationResult: Form[AdditionalMetadataEntryData] = additionalMetadataEntryForm.bindFromRequest()

      val errorFunction: Form[AdditionalMetadataEntryData] => Future[Result] = { formWithErrors: Form[AdditionalMetadataEntryData] =>
        for {
          reference <- consignmentService.getConsignmentRef(consignmentId, token)
        } yield {
          Ok(views.html.standard.additionalMetadataEntryMethod(consignmentId, reference, formWithErrors, request.token.name))
            .uncache()
        }
      }

      val successFunction: AdditionalMetadataEntryData => Future[Result] = { formData: AdditionalMetadataEntryData =>
        formData.metadataRoute match {
          case Some("manual") => Future.successful(Redirect(routes.AdditionalMetadataController.start(consignmentId)))
          case Some("csv") =>
            for {
              consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId, token)
              statusesToValue = consignmentStatusService.getStatusValues(consignmentStatuses, DraftMetadataType).values.headOption.flatten
              _ <-
                if (statusesToValue.isEmpty) {
                  consignmentStatusService.addConsignmentStatus(consignmentId, DraftMetadataType.id, InProgressValue.value, token)
                } else {
                  consignmentStatusService.updateConsignmentStatus(ConsignmentStatusInput(consignmentId, DraftMetadataType.id, Some(InProgressValue.value)), token)
                }
            } yield {
              Redirect(routes.DraftMetadataUploadController.draftMetadataUploadPage(consignmentId))
            }
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
