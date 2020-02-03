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

  val form = Form(
    mapping(
      "seriesNo" -> number
    )(SelectedSeriesData.apply)(SelectedSeriesData.unapply)
  )

  val mockedData: Seq[(String, String)] = Seq(("series1", "Series 1"), ("series2", "Series 2"), ("series3", "Series 3"))

  def seriesDetails(): Action[AnyContent] = Secure("OidcClient") { implicit request: Request[AnyContent] =>
    Ok(views.html.seriesDetails(mockedData, form))
  }

  def seriesSubmit(): Action[AnyContent] =  Secure("OidcClient") { implicit request: Request[AnyContent] =>
    Redirect(routes.SeriesDetailsController.seriesDetails())
  }
}

case class SelectedSeriesData (seriesId: Int)
