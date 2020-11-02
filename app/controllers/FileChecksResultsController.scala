package controllers

import java.util.UUID

import auth.TokenSecurity
import configuration.{FrontEndInfoConfiguration, GraphQLConfiguration, KeycloakConfiguration}
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import services.ConsignmentService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileChecksResultsController @Inject()(val controllerComponents: SecurityComponents,
                                            val keycloakConfiguration: KeycloakConfiguration,
                                            val graphqlConfiguration: GraphQLConfiguration,
                                            val consignmentService: ConsignmentService,
                                            val frontEndInfoConfiguration: FrontEndInfoConfiguration
                                           )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  private def getConsignmentFolderDetails(request: Request[AnyContent], consignmentId: UUID)
                                         (implicit requestHeader: RequestHeader): Future[ConsignmentFolderInfo] = {

    consignmentService.getConsignmentFolderInfo(consignmentId, request.token.bearerAccessToken)
      .map(consignmentInfo => ConsignmentFolderInfo(consignmentInfo.totalFiles, consignmentInfo.parentFolder.get))
  }

  def fileCheckResultsPage(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    getConsignmentFolderDetails(request, consignmentId).map(consignmentInfo => Ok(views.html.fileChecksResults(consignmentInfo)))
  }
}

case class ConsignmentFolderInfo(numberOfFiles: Int, parentFolder: String)