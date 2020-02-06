package controllers

import javax.inject.{Inject, Singleton}
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

@Singleton
class SeriesDetailsController @Inject()(val controllerComponents: SecurityComponents)
                                         extends Security[CommonProfile] with I18nSupport  {

  private val secureAction = Secure("OidcClient")

  val selectedSeriesForm = Form(
    mapping(
      "seriesId" -> nonEmptyText
    )(SelectedSeriesData.apply)(SelectedSeriesData.unapply)
  )

  // Mocked data until API implemented to show dropdown on page working
  val mockedAllSeriesData: Seq[(String, String)] = Seq(
    ("mockedSeries1", "Mocked Series 1"),
    ("mockedSeries2", "Mocked Series 2"),
    ("mockedSeries3", "Mocked Series 3"))

  def seriesDetails(): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.seriesDetails(mockedAllSeriesData, selectedSeriesForm))
  }

  // Submit returns current page until next page is ready
  def seriesSubmit(): Action[AnyContent] =  secureAction { implicit request: Request[AnyContent] =>
    Redirect(routes.SeriesDetailsController.seriesDetails())
  }
}

case class SelectedSeriesData (seriesId: String)
