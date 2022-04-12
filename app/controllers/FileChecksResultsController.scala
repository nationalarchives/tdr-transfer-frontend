package controllers

import java.util.UUID

import auth.TokenSecurity
import configuration.{FrontEndInfoConfiguration, GraphQLConfiguration, KeycloakConfiguration}
import javax.inject.{Inject, Singleton}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class FileChecksResultsController @Inject()(val controllerComponents: SecurityComponents,
                                            val keycloakConfiguration: KeycloakConfiguration,
                                            val graphqlConfiguration: GraphQLConfiguration,
                                            val consignmentService: ConsignmentService,
                                            val frontEndInfoConfiguration: FrontEndInfoConfiguration
                                           )(implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  def fileCheckResultsPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken).map(fileCheck => {
      val parentFolder = fileCheck.parentFolder.getOrElse(throw new IllegalStateException(s"No parent folder found for consignment: '$consignmentId'"))
      if(fileCheck.allChecksSucceeded) {
        val consignmentInfo = ConsignmentFolderInfo(
          fileCheck.totalFiles,
          parentFolder
        )
        Ok(views.html.standard.fileChecksResults(consignmentInfo, consignmentId, request.token.name))
      } else {
        val fileStatusList = fileCheck.files.flatMap(_.fileStatus)
        Ok(views.html.fileChecksFailed(request.token.name, isJudgmentUser = false, fileStatusList))
        }
    })
  }

  def judgmentFileCheckResultsPage(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken).flatMap(fileCheck => {
      if (fileCheck.allChecksSucceeded) {
        for {
          filepath <- consignmentService.getConsignmentFilePath(consignmentId, request.token.bearerAccessToken)
          reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        } yield {
          val filename = filepath.files.head.metadata.clientSideOriginalFilePath
            .getOrElse(throw new IllegalStateException(s"Filename cannot be found for judgment upload: '$consignmentId'"))
          Ok(views.html.judgment.judgmentFileChecksResults(filename, consignmentId, reference.consignmentReference, request.token.name))
        }
      } else {
        Future(Ok(views.html.fileChecksFailed(request.token.name, isJudgmentUser = true)))
      }
    })
  }
}

case class ConsignmentFolderInfo(numberOfFiles: Int, parentFolder: String)
