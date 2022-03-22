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
import services.{ConsignmentService, SeriesService}

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SeriesDetailsController @Inject()(val controllerComponents: SecurityComponents,
                                        val keycloakConfiguration: KeycloakConfiguration,
                                        seriesService: SeriesService,
                                        val consignmentService: ConsignmentService
                                        )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  val selectedSeriesForm = Form(
    mapping(
      "series" -> text.verifying("Select a series reference", t => !t.isEmpty)
    )(SelectedSeriesData.apply)(SelectedSeriesData.unapply)
  )

  private def getSeriesDetails(consignmentId: UUID, request: Request[AnyContent], status: Status, form: Form[SelectedSeriesData])
                              (implicit requestHeader: RequestHeader) = {
    seriesService.getSeriesForUser(request.token)
      .map({series =>
        val seriesFormData = series.map(s => (s.seriesid.toString, s.code))
        status(views.html.standard.seriesDetails(consignmentId, seriesFormData, form, request.token.name))
      })
  }

  def seriesDetails(consignmentId: UUID): Action[AnyContent] = standardUserAction { implicit request: Request[AnyContent] =>
    //Need to get the status for the series

    getSeriesDetails(consignmentId, request, Ok, selectedSeriesForm)
  }

  def seriesSubmit(consignmentId: UUID): Action[AnyContent] =  standardUserAction { implicit request: Request[AnyContent] =>
    //No longer need to create a consignment
    //Update with a series id if the status is not completed

    val formValidationResult: Form[SelectedSeriesData] = selectedSeriesForm.bindFromRequest()

    val errorFunction: Form[SelectedSeriesData] => Future[Result] = { formWithErrors: Form[SelectedSeriesData] =>
      getSeriesDetails(consignmentId, request, BadRequest, formWithErrors)
    }

    val successFunction: SelectedSeriesData => Future[Result] = { formData: SelectedSeriesData =>
      if (request.token.isJudgmentUser) {
        Future(Redirect(routes.BeforeUploadingController.beforeUploading(consignmentId)))
      } else {
        consignmentService.updateSeriesIdOfConsignment(consignmentId, UUID.fromString(formData.seriesId), request.token.bearerAccessToken)
        Future(Redirect(routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId)))
      }
    }

    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }
}

case class SelectedSeriesData (seriesId: String)
