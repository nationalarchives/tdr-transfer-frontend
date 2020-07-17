package controllers

import java.util.UUID

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


  private def getRecordProcessingProgress(consignmentId: UUID): Unit = {
  //    Create function in ConsignmentService to get the current progress of AVmetadata file processing.
  //    Call service function here & do action to load record processing page.
  //    Call this function in recordsPage function below.
  //    Keeping this private helps so public functions can't directly access the API.
  //    This will end up being similar to the seriesDetails function and private getSeriesDetails in SeriesDetailsController
  }

  def recordsPage(consignmentId: UUID): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.records(consignmentId))
  }
}
