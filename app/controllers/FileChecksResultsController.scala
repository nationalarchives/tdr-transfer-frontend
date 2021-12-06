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
    consignmentService.getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken).map(fileCheck => {
      val parentFolder = fileCheck.parentFolder.getOrElse(throw new IllegalStateException(s"No parent folder found for consignment: '$consignmentId'"))
      if(fileCheck.allChecksSucceeded) {
        if (request.token.isJudgmentUser) {
          val result = for {
            files <- consignmentService.getConsignmentFilePath(consignmentId, request.token.bearerAccessToken)
            filename = files.files.head.metadata.clientSideOriginalFilePath.get
          } yield filename
          val filename = Await.result(result, Duration.Inf)
          Ok(views.html.judgment.judgmentFileChecksResults(filename, consignmentId))
        } else {
          val consignmentInfo = ConsignmentFolderInfo(
            fileCheck.totalFiles,
            parentFolder
          )
          Ok(views.html.standard.fileChecksResults(consignmentInfo, consignmentId))
        }
      } else {
        Ok(views.html.standard.fileChecksFailed())
      }
    })
  }
}

case class ConsignmentFolderInfo(numberOfFiles: Int, parentFolder: String)
