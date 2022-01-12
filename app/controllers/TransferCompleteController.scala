package controllers

import java.util.UUID

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import javax.inject.Inject
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import services.ConsignmentService

import scala.concurrent.{ExecutionContext, Future}

class TransferCompleteController @Inject()(val controllerComponents: SecurityComponents,
                                           val keycloakConfiguration: KeycloakConfiguration,
                                           consignmentService: ConsignmentService)
                                          (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  private def getConsignmentReference(request: Request[AnyContent], consignmentId: UUID)
                                     (implicit requestHeader: RequestHeader): Future[String] = {
    consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map(r => r.consignmentReference)
  }

  def transferComplete(consignmentId: UUID): Action[AnyContent] = standardUserAction { implicit request: Request[AnyContent] =>
    getConsignmentReference(request, consignmentId)
      .map { consignmentReference =>
        Ok(views.html.standard.transferComplete(consignmentReference))
      }
  }

  def judgmentTransferComplete(consignmentId: UUID): Action[AnyContent] = judgmentUserAction { implicit request: Request[AnyContent] =>
    getConsignmentReference(request, consignmentId)
      .map { consignmentReference =>
        Ok(views.html.judgment.judgmentComplete(consignmentReference))
      }
  }
}

