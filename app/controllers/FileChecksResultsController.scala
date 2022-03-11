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
        val fileStatus = fileCheck.files.find(!_.fileStatus.contains("Success")).get.fileStatus.get
        Ok(views.html.fileChecksFailed(request.token.name, isJudgmentUser = false, fileStatus))
        }
    })
  }

  def judgmentFileCheckResultsPage(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken).flatMap(fileCheck => {
      if (fileCheck.allChecksSucceeded) {
        consignmentService.getConsignmentFilePath(consignmentId, request.token.bearerAccessToken).map(files => {
          val filename = files.files.head.metadata.clientSideOriginalFilePath
            .getOrElse(throw new IllegalStateException(s"Filename cannot be found for judgment upload: '$consignmentId'"))
          Ok(views.html.judgment.judgmentFileChecksResults(filename, consignmentId, request.token.name))
        })
      } else {
        Future(Ok(views.html.fileChecksFailed(request.token.name, isJudgmentUser = true, "Normal")))
      }
    })
  }
}

case class ConsignmentFolderInfo(numberOfFiles: Int, parentFolder: String)
