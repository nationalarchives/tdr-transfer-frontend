package controllers

import java.util.UUID
import auth.TokenSecurity
import configuration.{FrontEndInfoConfiguration, GraphQLConfiguration, KeycloakConfiguration}

import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, RequestHeader}
import services.ConsignmentService

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

@Singleton
class FileChecksResultsController @Inject()(val controllerComponents: SecurityComponents,
                                            val keycloakConfiguration: KeycloakConfiguration,
                                            val graphqlConfiguration: GraphQLConfiguration,
                                            val consignmentService: ConsignmentService,
                                            val frontEndInfoConfiguration: FrontEndInfoConfiguration
                                           )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  def fileCheckResultsPage(consignmentId: UUID): Action[AnyContent] = secureAction.async { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken).flatMap(fileCheck => {
      if (fileCheck.allChecksSucceeded) {
        if (request.token.isJudgmentUser) {
          consignmentService.getConsignmentFilePath(consignmentId, request.token.bearerAccessToken).map(files => {
            val filename = files.files.head.metadata.clientSideOriginalFilePath
              .getOrElse(throw new IllegalStateException(s"Filename cannot be found for judgment upload: '$consignmentId'"))
            Ok(views.html.judgment.judgmentFileChecksResults(filename, consignmentId))
          })
        } else {
          val parentFolder = fileCheck.parentFolder.getOrElse(throw new IllegalStateException(s"No parent folder found for consignment: '$consignmentId'"))
          val consignmentInfo = ConsignmentFolderInfo(
            fileCheck.totalFiles,
            parentFolder
          )
          Future(Ok(views.html.standard.fileChecksResults(consignmentInfo, consignmentId)))
        }
      } else {
        Future(Ok(views.html.standard.fileChecksFailed()))
      }
    })
  }
}

case class ConsignmentFolderInfo(numberOfFiles: Int, parentFolder: String)
