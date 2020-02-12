package controllers

import auth.TokenSecurity
import configuration.GraphQLConfiguration
import graphql.codegen.query.getSeries.{Data, Variables, document}
import javax.inject.{Inject, Singleton}
import org.keycloak.representations.AccessToken
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.Configuration
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import uk.gov.nationalarchives.tdr.keycloak.KeycloakUtils

import scala.concurrent.ExecutionContext

//noinspection ScalaStyle
@Singleton
class SeriesDetailsController @Inject()(val controllerComponents: SecurityComponents,
                                        val graphqlConfiguration: GraphQLConfiguration,
                                        val configuration: Configuration
                                        )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  private val secureAction = Secure("OidcClient")
  private val client = graphqlConfiguration.getClient[Data, Variables]()
  private val authBaseUrl: String = configuration.get[String]("auth.url")


  val selectedSeriesForm = Form(
    mapping(
      "seriesId" -> nonEmptyText
    )(SelectedSeriesData.apply)(SelectedSeriesData.unapply)
  )

  def seriesDetails(): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    val body = KeycloakUtils(s"$authBaseUrl/auth").verifyToken(request.token.getValue)
      .map(t => t.getOtherClaims.get("body").asInstanceOf[String])
    val variables: Variables = new Variables(body)

    client.getResult(request.token, document, Some(variables)).map(data => {
      if (data.data.isDefined) {
        val seriesData: Seq[(String, String)] = data.data.get.getSeries.map(s => (s.code.getOrElse(""), s.description.getOrElse("")))
        Ok(views.html.seriesDetails(seriesData, selectedSeriesForm))
      } else {
        Ok(views.html.index())
      }
    })

  }

  // Submit returns current page until next page is ready
  def seriesSubmit(): Action[AnyContent] =  secureAction { implicit request: Request[AnyContent] =>
    Redirect(routes.SeriesDetailsController.seriesDetails())
  }
}

case class SelectedSeriesData (seriesId: String)
