package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.{ConsignmentService, ConsignmentStatusService, SeriesService}
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SeriesDetailsController @Inject()(val controllerComponents: SecurityComponents,
                                        val keycloakConfiguration: KeycloakConfiguration,
                                        seriesService: SeriesService,
                                        val consignmentService: ConsignmentService,
                                        val consignmentStatusService: ConsignmentStatusService
                                       )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  val selectedSeriesForm: Form[SelectedSeriesData] = Form(
    mapping(
      "series" -> text.verifying("Select a series reference", t => t.nonEmpty)
    )(SelectedSeriesData.apply)(SelectedSeriesData.unapply)
  )

  def seriesDetails(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    getSeriesDetails(consignmentId, request, Ok, selectedSeriesForm)
  }

  def seriesSubmit(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val formValidationResult: Form[SelectedSeriesData] = selectedSeriesForm.bindFromRequest()

    val errorFunction: Form[SelectedSeriesData] => Future[Result] = { formWithErrors: Form[SelectedSeriesData] =>
      getSeriesDetails(consignmentId, request, BadRequest, formWithErrors)
    }

    val successFunction: SelectedSeriesData => Future[Result] = { formData: SelectedSeriesData =>
      for {
        consignmentStatus <- consignmentStatusService.getConsignmentStatus(consignmentId, request.token.bearerAccessToken)
        seriesStatus = consignmentStatus.flatMap(_.series)
      } yield seriesStatus match {
        case Some("Completed") => Redirect(routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId))
        case _ => consignmentService.updateSeriesIdOfConsignment(consignmentId, UUID.fromString(formData.seriesId), request.token.bearerAccessToken)
          Redirect(routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId))
      }
    }

    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }

  private def getSeriesDetails(consignmentId: UUID, request: Request[AnyContent], status: Status, form: Form[SelectedSeriesData])
                              (implicit requestHeader: RequestHeader) = {
    for {
      consignmentStatus <- consignmentStatusService.consignmentStatusSeries(consignmentId, request.token.bearerAccessToken)
      seriesStatus = consignmentStatus.flatMap(_.currentStatus.series)
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      result <- seriesStatus match {
        case Some("Completed") =>
          val seriesFormData: List[(String, String)] = List(
            consignmentStatus.flatMap(_.series.map{
              series => (series.seriesid.toString, series.code)
            }).get
          )
          Future(Ok(views.html.standard.seriesDetailsAlreadyConfirmed(consignmentId, reference,
            seriesFormData, selectedSeriesForm, request.token.name)).uncache())
        case _ =>
          seriesService.getSeriesForUser(request.token).map { series =>
            val seriesFormData: List[(String, String)] = series.map(s => (s.seriesid.toString, s.code))
            status(views.html.standard.seriesDetails(consignmentId, reference, seriesFormData, form, request.token.name)).uncache()
          }
      }
    } yield result
  }
}

case class SelectedSeriesData(seriesId: String)
