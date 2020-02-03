package controllers

import javax.inject.{Inject, Singleton}
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

@Singleton
class SeriesDetailsController @Inject()(val controllerComponents: SecurityComponents)
                                         extends Security[CommonProfile] with I18nSupport  {

  def seriesDetails(): Action[AnyContent] = Secure("OidcClient") { implicit request: Request[AnyContent] =>
    Ok(views.html.seriesDetails())
  }
}
