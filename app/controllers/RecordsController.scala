package controllers

import java.util.UUID

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import services.ConsignmentService

import scala.concurrent.ExecutionContext

@Singleton
class RecordsController @Inject()(val controllerComponents: SecurityComponents,
                                  val graphqlConfiguration: GraphQLConfiguration,
                                  val keycloakConfiguration: KeycloakConfiguration,
                                  consignmentService: ConsignmentService
                                 )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  private def getRecordProcessingProgress(request: Request[AnyContent], status: Status, consignmentId: UUID)(implicit requestHeader: RequestHeader) = {
    consignmentService.getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken)
      .map({fileChecks =>
        status(views.html.records(
          consignmentId,
          fileChecks.totalFiles,
          (fileChecks.fileChecks.antivirusProgress.filesProcessed/ fileChecks.totalFiles) * 100)
        )
      })
  }

  def recordsPage(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    getRecordProcessingProgress(request, Ok, consignmentId)
  }
}
