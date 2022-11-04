package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BeforeUploadingController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def beforeUploading(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map(reference => Ok(views.html.judgment.judgmentBeforeUploading(consignmentId, reference, request.token.name)))
  }
}
