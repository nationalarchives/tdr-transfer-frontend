package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetSeries.getSeries._
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

import scala.concurrent.ExecutionContext

@Singleton
class SeriesDetailsController @Inject()(val controllerComponents: SecurityComponents,
                                        val graphqlConfiguration: GraphQLConfiguration,
                                        val keycloakConfiguration: KeycloakConfiguration
                                        )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  private val secureAction = Secure("OidcClient")
  private val client = graphqlConfiguration.getClient[Data, Variables]()

  val selectedSeriesForm = Form(
    mapping(
      "seriesId" -> nonEmptyText
    )(SelectedSeriesData.apply)(SelectedSeriesData.unapply)
  )

  def seriesDetails(): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    val body = keycloakConfiguration.verifyToken(request.token.getValue)
      .map(t => t.getOtherClaims.get("body").asInstanceOf[String])
    val variables: Variables = new Variables(body)

    client.getResult(request.token, document, Some(variables)).map(data => {
      if (data.data.isDefined) {
        val seriesData: Seq[(String, String)] = data.data.get.getSeries.map(s => (s.seriesid.toString, s.code.getOrElse("")))
        Ok(views.html.seriesDetails(seriesData, selectedSeriesForm))
      } else {
        Ok(views.html.error(data.errors.map(e => e.message).mkString))
      }
    })

  }

  // Submit returns current page until next page is ready
  def seriesSubmit(): Action[AnyContent] =  secureAction { implicit request: Request[AnyContent] =>
    Redirect(routes.SeriesDetailsController.seriesDetails())
  }
}

case class SelectedSeriesData (seriesId: String)
