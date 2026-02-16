package controllers

import auth.UnprotectedPageController
import configuration.ApplicationConfig

import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import uk.gov.nationalarchives.tdr.schemautils.ConfigUtils

import scala.io.Source
import scala.util.Using

@Singleton
class HelpController @Inject() (securityComponents: SecurityComponents, val applicationConfig: ApplicationConfig)
    extends UnprotectedPageController(securityComponents)
    with I18nSupport {
  def help(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.help(request.isLoggedIn, request.name, request.isJudgmentUser, applicationConfig.blockLegalStatus))
  }

  def judgmentHelp(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    Ok(views.html.judgment.judgmentHelp(request.isLoggedIn, request.name))
  }

  def metadataQuickGuide(): Action[AnyContent] = Action { implicit request: Request[AnyContent] =>
    val METADATA_GUIDANCE_LOCATION = ConfigUtils.mapToMetadataEnvironmentFile("/guidance/metadata-template.json")
    val nodeSchema = getClass.getResourceAsStream(METADATA_GUIDANCE_LOCATION)
    val source = Source.fromInputStream(nodeSchema)
    val guideContent = Using(source)(_.mkString).get
    val propertyNameMapper = ConfigUtils.loadConfiguration.propertyToOutputMapper("tdrFileHeader")
    Ok(views.html.metadataquickguide(request.isLoggedIn, request.name, request.isJudgmentUser, guideContent, propertyNameMapper))
  }
}
