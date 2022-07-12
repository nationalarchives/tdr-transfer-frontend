package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class ClosureMetadataController @Inject()(val controllerComponents: SecurityComponents,
                                          val keycloakConfiguration: KeycloakConfiguration,
                                          val consignmentService: ConsignmentService
                                         )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {


  def getClosureMetadata(consignmentId: UUID, rootFolderId: UUID): Action[AnyContent]
          = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>

    for {
      details <- consignmentService.getConsignmentDetails(consignmentId, request.token.bearerAccessToken)
    } yield Ok(views.html.closureMetadata(consignmentId, rootFolderId, details.parentFolder.getOrElse(""),
      details.consignmentReference, request.token.name))
  }
}
