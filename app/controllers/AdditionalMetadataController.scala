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
 /* def getPaginatedFiles(consignmentId: UUID, page: Int, limit: Option[Int]): Action[AnyContent] = standardTypeAction(consignmentId)
  { implicit request: Request[AnyContent] =>
    //val hasPrevious = page = 0
    //val hasNext = page != totalPage
    //'page - 1' so that the page number in the url matches the page number in the pagination bar
    consignmentService.getConsignmentPaginatedFile(consignmentId, page - 1, limit, request.token.bearerAccessToken)
      .map { paginatedFiles =>
        val nextCursor = paginatedFiles.paginatedFiles.edges.get.last.get.node.fileName
        println(nextCursor)
        println("total files: " + paginatedFiles.totalFiles)
        println("start cursor: " + paginatedFiles.paginatedFiles.pageInfo.startCursor)
        println("end cursor: " + paginatedFiles.paginatedFiles.pageInfo.endCursor)
        println("has next page: " + paginatedFiles.paginatedFiles.pageInfo.hasNextPage)
        println("has previous page: " + paginatedFiles.paginatedFiles.pageInfo.hasPreviousPage)
        println("Edges: " + paginatedFiles.paginatedFiles.edges)
        //val limit = 2
        //Int.MaxValue is bad because if api has a variable set then, clicking next/prev will give you offset variable max
        //val limit2 = if(limit.isDefined) limit.get else Int.MaxValue
        val totalFiles = paginatedFiles.totalFiles
        val totalPages = Math.ceil(totalFiles/limit.getOrElse(1)) //totalItems divided by the limit rounded up
        println(totalPages)
        val parentFolder = paginatedFiles.parentFolder.get
        Ok(views.html.standard.additionalMetadataOLD(consignmentId,
          "consignmentRef",
          request.token.name,
          parentFolder,
          totalFiles,
          totalPages.toInt,
          limit,
          page,
          paginatedFiles.paginatedFiles.edges.get.flatten)
        )
      }
  }*/

/*  def getPaginatedFiles2(consignmentId: UUID, page: Int, limit: Option[Int]): Action[AnyContent] = standardTypeAction(consignmentId)
  { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentPaginatedFile2(consignmentId, page - 1, limit, request.token.bearerAccessToken)
      .map { paginatedFiles =>
        val totalFiles = paginatedFiles.paginatedFiles2.numberOfFilesInFolder
        val totalPages = Math.ceil(totalFiles/limit.getOrElse(paginatedFiles.paginatedFiles2.limit.get).toDouble) //totalItems divided by the limit rounded up
        val parentFolder = paginatedFiles.parentFolder.get
        Ok(views.html.standard.additionalMetadata(consignmentId,
          "consignmentRef",
          request.token.name,
          parentFolder,
          totalFiles,
          totalPages.toInt,
          limit,
          page,
          paginatedFiles.paginatedFiles2.fileEdge)
        )
      }
  }*/

  def getPaginatedFiles(consignmentId: UUID, page: Int, limit: Option[Int]): Action[AnyContent] = standardTypeAction(consignmentId)
  { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentPaginatedFile(consignmentId, page - 1, limit, request.token.bearerAccessToken)
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
          paginatedFiles.paginatedFiles.edges.get.flatten)
        )
      }
  }
}
