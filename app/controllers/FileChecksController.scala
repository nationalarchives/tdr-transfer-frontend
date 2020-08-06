package controllers

import java.util.UUID

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import services.ConsignmentService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileChecksController @Inject()(val controllerComponents: SecurityComponents,
                                     val graphqlConfiguration: GraphQLConfiguration,
                                     val keycloakConfiguration: KeycloakConfiguration,
                                     consignmentService: ConsignmentService
                                    )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  private def getRecordProcessingProgress(request: Request[AnyContent], consignmentId: UUID)
                                         (implicit requestHeader: RequestHeader): Future[FileChecksProgress] = {
    consignmentService.getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken)
      .map{
        fileCheckProgress => {
          FileChecksProgress(fileCheckProgress.totalFiles, fileCheckProgress.fileChecks.antivirusProgress.filesProcessed * 100 / fileCheckProgress.totalFiles)
        }
      }
  }

  def recordProcessingPage(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    getRecordProcessingProgress(request, consignmentId)
      .map {
        fileChecks => Ok(views.html.fileChecksProgress(
          consignmentId,
          fileChecks.totalFiles,
          fileChecks.avMetadataProgressPercentage
        ))
      }
  }
}

case class FileChecksProgress(totalFiles: Int, avMetadataProgressPercentage: Int)
