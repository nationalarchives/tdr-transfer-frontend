package controllers

import auth.OidcSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

@Singleton
class RecordsController @Inject()(val controllerComponents: SecurityComponents,
                                  val graphqlConfiguration: GraphQLConfiguration,
                                  val keycloakConfiguration: KeycloakConfiguration
                                 ) extends OidcSecurity with I18nSupport {

  def recordsPage(consignmentId: Long): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.records())
  }
}
