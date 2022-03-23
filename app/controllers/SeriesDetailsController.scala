package controllers

import java.util.UUID

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.{ConsignmentService, ConsignmentStatusService, SeriesService}
import viewsapi.Caching.preventCaching

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SeriesDetailsController @Inject()(val controllerComponents: SecurityComponents,
                                        val keycloakConfiguration: KeycloakConfiguration,
                                        seriesService: SeriesService,
                                        val consignmentService: ConsignmentService,
                                        val consignmentStatusService: ConsignmentStatusService,
                                        )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  val selectedSeriesForm = Form(
    mapping(
      "series" -> text.verifying("Select a series reference", t => !t.isEmpty)
    )(SelectedSeriesData.apply)(SelectedSeriesData.unapply)
  )

  private def getSeriesDetails(consignmentId: UUID, request: Request[AnyContent], status: Status, form: Form[SelectedSeriesData])
                              (implicit requestHeader: RequestHeader) = {
    consignmentStatusService.consignmentStatus(consignmentId, request.token.bearerAccessToken).flatMap {
      consignmentStatus =>
        val seriesStatus = consignmentStatus.flatMap(_.series)
        seriesStatus match {
          case Some("Completed") => Future(Ok(views.html.standard.seriesDetailsAlreadyConfirmed(consignmentId, request.token.name)).uncache())
          case _ =>
            seriesService.getSeriesForUser(request.token)
              .map({ series =>
                val seriesFormData = series.map(s => (s.seriesid.toString, s.code))
                status(views.html.standard.seriesDetails(consignmentId, seriesFormData, form, request.token.name)).uncache()
              })
        }
    }
  }

  def seriesDetails(consignmentId: UUID): Action[AnyContent] = standardUserAction { implicit request: Request[AnyContent] =>
    getSeriesDetails(consignmentId, request, Ok, selectedSeriesForm)
  }

  def seriesSubmit(consignmentId: UUID): Action[AnyContent] =  standardUserAction { implicit request: Request[AnyContent] =>
    val formValidationResult: Form[SelectedSeriesData] = selectedSeriesForm.bindFromRequest()

    val errorFunction: Form[SelectedSeriesData] => Future[Result] = { formWithErrors: Form[SelectedSeriesData] =>
      getSeriesDetails(consignmentId, request, BadRequest, formWithErrors)
    }

    val successFunction: SelectedSeriesData => Future[Result] = { formData: SelectedSeriesData =>
      if (request.token.isJudgmentUser) {
        Future(Redirect(routes.BeforeUploadingController.beforeUploading(consignmentId)))
      } else {
        for {
          consignmentStatus <- consignmentStatusService.consignmentStatus(consignmentId, request.token.bearerAccessToken)
          confirmTransferStatus = consignmentStatus.flatMap(_.series)
        } yield confirmTransferStatus match {
          case Some("Completed") => Redirect(routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId))
          case _ => consignmentService.updateSeriesIdOfConsignment(consignmentId, UUID.fromString(formData.seriesId), request.token.bearerAccessToken)
            Redirect(routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId))
        }
      }
    }

    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }
}

case class SelectedSeriesData (seriesId: String)
