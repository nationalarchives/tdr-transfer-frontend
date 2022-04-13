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
    for {
      fileCheck <- consignmentService.getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken)
      parentFolder = fileCheck.parentFolder.getOrElse(throw new IllegalStateException(s"No parent folder found for consignment: '$consignmentId'"))
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield {
      if (fileCheck.allChecksSucceeded) {
        val consignmentInfo = ConsignmentFolderInfo(
          fileCheck.totalFiles,
          parentFolder
        )
        Ok(views.html.standard.fileChecksResults(consignmentInfo, consignmentId, request.token.name))
      } else {
        val fileStatusList = fileCheck.files.flatMap(_.fileStatus)
        Ok(views.html.fileChecksFailed(request.token.name, reference.consignmentReference, isJudgmentUser = false, fileStatusList))
      }
    }
  }

  def judgmentFileCheckResultsPage(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      fileCheck <- consignmentService.getConsignmentFileChecks(consignmentId, request.token.bearerAccessToken)
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      result <- fileCheck.allChecksSucceeded match {
        case true => consignmentService.getConsignmentFilePath(consignmentId, request.token.bearerAccessToken).flatMap(files => {
          val filename = files.files.head.metadata.clientSideOriginalFilePath.get
          Future(Ok(views.html.judgment.judgmentFileChecksResults(filename, consignmentId, reference.consignmentReference, request.token.name)))
        })
        case _ => Future(Ok(views.html.fileChecksFailed(request.token.name, reference.consignmentReference, isJudgmentUser = true)))
      }
    } yield result
  }
}

case class ConsignmentFolderInfo(numberOfFiles: Int, parentFolder: String)
