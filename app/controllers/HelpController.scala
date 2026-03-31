package controllers

import auth.UnprotectedPageController
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import uk.gov.nationalarchives.tdr.schema.generated.MetadataTemplate
import uk.gov.nationalarchives.tdr.schemautils.ConfigUtils

import javax.inject.{Inject, Singleton}

@Singleton
class HelpController @Inject() (securityComponents: SecurityComponents) extends UnprotectedPageController(securityComponents) with I18nSupport {
  def help(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.help(request.isLoggedIn, request.name, request.isJudgmentUser))
  }

  def judgmentHelp(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.judgment.judgmentHelp(request.isLoggedIn, request.name))
  }

  def metadataQuickGuide(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val propertyNameMapper = ConfigUtils.loadConfiguration.propertyToOutputMapper("tdrFileHeader")
    Ok(views.html.metadataquickguide(request.isLoggedIn, request.name, request.isJudgmentUser, MetadataTemplate.all, propertyNameMapper))
  }
}
