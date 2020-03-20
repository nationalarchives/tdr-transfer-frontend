package controllers

import auth.OidcSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import validation.ValidatedActions

import scala.concurrent.ExecutionContext

@Singleton
class UploadController @Inject()(val controllerComponents: SecurityComponents,
                                 val graphqlConfiguration: GraphQLConfiguration,
                                 val keycloakConfiguration: KeycloakConfiguration)
                                (implicit val ec: ExecutionContext) extends ValidatedActions with I18nSupport {

  def uploadPage(consignmentId: Long): Action[AnyContent] = transferAgreementExistsAction(consignmentId) { implicit request: Request[AnyContent] =>
    Ok(views.html.upload())
  }
}
