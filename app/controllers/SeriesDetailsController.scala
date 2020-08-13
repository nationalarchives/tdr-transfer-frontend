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
                                        consignmentService: ConsignmentService
                                        )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  val selectedSeriesForm = Form(
    mapping(
      "series" -> nonEmptyText
    )(SelectedSeriesData.apply)(SelectedSeriesData.unapply)
  )

  private def getSeriesDetails(request: Request[AnyContent], status: Status, form: Form[SelectedSeriesData])(implicit requestHeader: RequestHeader) = {
    seriesService.getSeriesForUser(request.token)
      .map({series =>
        val seriesFormData = series.map(s => (s.seriesid.toString, s.code.getOrElse("")))
        status(views.html.seriesDetails(seriesFormData, form))
      })
  }

  def seriesDetails(): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    getSeriesDetails(request, Ok, selectedSeriesForm)
  }

  def seriesSubmit(): Action[AnyContent] =  secureAction.async { implicit request: Request[AnyContent] =>
    val formValidationResult: Form[SelectedSeriesData] = selectedSeriesForm.bindFromRequest()

    val errorFunction: Form[SelectedSeriesData] => Future[Result] = { formWithErrors: Form[SelectedSeriesData] =>
      getSeriesDetails(request, BadRequest, formWithErrors)
    }

    val successFunction: SelectedSeriesData => Future[Result] = { formData: SelectedSeriesData =>
      consignmentService
        .createConsignment(UUID.fromString(formData.seriesId), request.token.bearerAccessToken)
        .map(consignment => {
          Redirect(routes.TransferAgreementController.transferAgreement(consignment.consignmentid.get))
        })
    }

    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }
}

case class SelectedSeriesData (seriesId: String)
