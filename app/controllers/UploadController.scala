package controllers

import java.util.UUID

import configuration.{FrontEndInfoConfiguration, GraphQLConfiguration, KeycloakConfiguration}
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.Configuration
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import validation.ValidatedActions

import scala.concurrent.ExecutionContext

@Singleton
class UploadController @Inject()(val controllerComponents: SecurityComponents,
                                 val graphqlConfiguration: GraphQLConfiguration,
                                 val keycloakConfiguration: KeycloakConfiguration,
                                 val frontEndInfoConfiguration: FrontEndInfoConfiguration)
                                (implicit val ec: ExecutionContext) extends ValidatedActions with I18nSupport {

  def uploadPage(consignmentId: UUID): Action[AnyContent] = transferAgreementExistsAction(consignmentId) { implicit request: Request[AnyContent] =>
    Ok(views.html.upload(consignmentId, frontEndInfoConfiguration.frontEndInfo))
  }
}
