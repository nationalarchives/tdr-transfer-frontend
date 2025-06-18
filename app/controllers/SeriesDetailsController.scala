package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.{DropdownField, InputNameAndValue}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc._
import services.Statuses.{CompletedValue, InProgressValue, SeriesType}
import services.{ConsignmentService, ConsignmentStatusService, SeriesService}
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SeriesDetailsController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    seriesService: SeriesService,
    val consignmentService: ConsignmentService,
    val consignmentStatusService: ConsignmentStatusService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  val selectedSeriesForm: Form[SelectedSeriesData] = Form(
    mapping(
      "series" -> text.verifying("Select a series reference", t => t.nonEmpty)
    )(SelectedSeriesData.apply)(SelectedSeriesData.unapply)
  )

  def seriesDetails(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    getSeriesDetails(consignmentId, request, Ok, selectedSeriesForm)
  }

  def seriesSubmit(consignmentId: UUID): Action[AnyContent] = standardUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val formValidationResult: Form[SelectedSeriesData] = selectedSeriesForm.bindFromRequest()

    val errorFunction: Form[SelectedSeriesData] => Future[Result] = { formWithErrors: Form[SelectedSeriesData] =>
      getSeriesDetails(consignmentId, request, BadRequest, formWithErrors)
    }

    val successFunction: SelectedSeriesData => Future[Result] = { formData: SelectedSeriesData =>
      for {
        consignmentStatuses <- consignmentStatusService.getConsignmentStatuses(consignmentId)
        seriesStatus = consignmentStatusService.getStatusValues(consignmentStatuses, SeriesType).values.headOption.flatten
      } yield {
        seriesStatus match {
          case Some(CompletedValue.value) => Redirect(routes.TransferAgreementPart1Controller.transferAgreement(consignmentId))
          case _ =>
            consignmentService.updateSeriesIdOfConsignment(consignmentId, UUID.fromString(formData.seriesId), request.token.bearerAccessToken)
            Redirect(routes.TransferAgreementPart1Controller.transferAgreement(consignmentId))
        }
      }
    }

    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }

  private def getSeriesDetails(consignmentId: UUID, request: Request[AnyContent], status: Status, form: Form[SelectedSeriesData])(implicit requestHeader: RequestHeader) = {
    for {
      consignmentStatus <- consignmentStatusService.consignmentStatusSeries(consignmentId)
      series <- consignmentStatusService.getSeries(consignmentId)
      seriesStatus = consignmentStatus.map(_.value)
      reference <- consignmentService.getConsignmentRef(consignmentId)
      result <- seriesStatus match {
        case Some(CompletedValue.value) =>
          val seriesOption: InputNameAndValue = consignmentStatus
            .map(c => InputNameAndValue(series.getOrElse(""), series.getOrElse("").toString))
            .get

          Future(
            Ok(views.html.standard.seriesDetailsAlreadyConfirmed(consignmentId, reference, createDropDownField(List(seriesOption), selectedSeriesForm), request.token.name))
              .uncache()
          )
        case _ =>
          if (!seriesStatus.contains(InProgressValue.value)) {
            consignmentStatusService.addConsignmentStatus(consignmentId, "Series", InProgressValue.value)
          }
          seriesService.getSeriesForUser(request.token).map { series =>
            val options = series.map(series => InputNameAndValue(series.code, series.id.toString))
            status(views.html.standard.seriesDetails(consignmentId, reference, createDropDownField(options, form), request.token.name)).uncache()
          }
      }
    } yield result
  }

  def createDropDownField(options: List[InputNameAndValue], form: Form[SelectedSeriesData]): DropdownField = {
    val errors = form("series").errors.headOption match {
      case Some(formError) => formError.messages
      case None            => Nil
    }
    DropdownField(form("series").id, "", "", "", Nil, multiValue = false, options, None, isRequired = true, errors.toList)
  }
}

case class SelectedSeriesData(seriesId: String)
