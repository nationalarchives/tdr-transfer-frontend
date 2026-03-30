package controllers

import auth.UnprotectedPageController
import configuration.ApplicationConfig
import io.circe.generic.auto._
import io.circe.jawn.decode
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import viewsapi.DisallowedPuid

import scala.io.Source
import scala.util.Using

@Singleton
class FaqController @Inject() (securityComponents: SecurityComponents, val applicationConfig: ApplicationConfig)
    extends UnprotectedPageController(securityComponents)
    with I18nSupport {

  private val disallowedPuids: List[DisallowedPuid] = {
    val stream = getClass.getResourceAsStream("/puids/disallowed-puids.json")
    val json = Using(Source.fromInputStream(stream))(_.mkString).getOrElse("[]")
    decode[List[DisallowedPuid]](json).getOrElse(List.empty)
  }

  def faq(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.faq(request.isLoggedIn, request.name, disallowedPuids))
  }

  def judgmentFaq(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.judgment.judgmentFaq(request.isLoggedIn, request.name))
  }
}
