package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class AdditionalMetadataController @Inject () (val consignmentService: ConsignmentService,
                                               val keycloakConfiguration: KeycloakConfiguration,
                                               val controllerComponents: SecurityComponents
                                              ) extends TokenSecurity {

  def start(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignment <- consignmentService.getConsignmentDetails(consignmentId, request.token.bearerAccessToken)
      response <- consignment.parentFolder match {
        case Some(folder) =>
          Future(Ok(views.html.standard.additionalMetadataStart(folder, consignment.consignmentReference, consignmentId, request.token.name)))
        case None => Future.failed(new IllegalStateException("Parent folder not found"))
      }
    } yield response
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.time.{LocalDateTime, ZonedDateTime}
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.ExecutionContext

class AdditionalMetadataController @Inject()(val controllerComponents: SecurityComponents,
                                             val keycloakConfiguration: KeycloakConfiguration,
                                             val consignmentService: ConsignmentService)
                                            (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  def getPaginatedFiles(consignmentId: UUID, page: Int, limit: Option[Int], selectedFolderId: UUID): Action[AnyContent] = standardTypeAction(consignmentId)
  { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentPaginatedFile(consignmentId, page - 1, limit, selectedFolderId, request.token.bearerAccessToken)
      .map { paginatedFiles =>
        val totalFiles = paginatedFiles.totalFiles
        val totalPages = paginatedFiles.paginatedFiles.totalPages.get
        val parentFolder = paginatedFiles.parentFolder.get
        Ok(views.html.standard.additionalMetadata(consignmentId,
          "consignmentRef", //Use
          request.token.name,
          parentFolder,
          totalFiles,
          totalPages,
          limit,
          page,
          selectedFolderId,
          paginatedFiles.paginatedFiles.edges.get.flatten)
        )
      }
  }
}
