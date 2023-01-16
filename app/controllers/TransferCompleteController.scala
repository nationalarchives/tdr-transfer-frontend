package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class TransferCompleteController @Inject() (
    val controllerComponents: SecurityComponents,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def transferComplete(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map { consignmentReference =>
        Ok(views.html.standard.transferComplete(consignmentId, consignmentReference, request.token.name))
      }
  }

  def judgmentTransferComplete(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map { consignmentReference =>
        Ok(views.html.judgment.judgmentComplete(consignmentReference, request.token.name))
      }
  }
}
