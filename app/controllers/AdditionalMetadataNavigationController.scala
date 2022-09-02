package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.AdditionalMetadataNavigationController.{NodesFormData, NodesToDisplay, ResultsCount}
import graphql.codegen.GetConsignmentPaginatedFiles.getConsignmentPaginatedFiles.GetConsignment.PaginatedFiles
import org.pac4j.play.scala.SecurityComponents
import play.api.cache.redis.{CacheApi, RedisSet, SynchronousResult}
import play.api.data.Form
import play.api.data.Forms.{boolean, list, mapping, nonEmptyText, number, optional, seq, text}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.ConsignmentService
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class AdditionalMetadataNavigationController @Inject()(val consignmentService: ConsignmentService,
                                                       val keycloakConfiguration: KeycloakConfiguration,
                                                       val controllerComponents: SecurityComponents,
                                                       val cacheApi: CacheApi
                                                      ) extends TokenSecurity {

  implicit class CacheHelper(cache: RedisSet[UUID, SynchronousResult]) {

    def updateCache(formData: NodesFormData, selectedFolderId: UUID): Unit = {
      val allNodes: List[String] = formData.allNodes
      val selected: List[String] = formData.selected
      val deselectedFiles: List[String] = allNodes.filter(id => !selected.contains(id))

      cache.addSelectedIds(selected)
      cache.removedDeselectedIds(deselectedFiles)

      //Deselect the parent folder if a child file has been deselected
      if (deselectedFiles.nonEmpty && cache.contains(selectedFolderId)) cache.remove(selectedFolderId)
    }

    def addSelectedIds(elems: List[String]): Unit = {
      elems.foreach(id => cache.add(UUID.fromString(id)))
    }

    def removedDeselectedIds(deselectedFiles: List[String]): Unit = {
      deselectedFiles.foreach(id => if (cache.contains(UUID.fromString(id))) cache.remove(UUID.fromString(id)))
    }
  }

  val navigationForm: Form[NodesFormData] = Form(
    mapping(
      "nodes" -> seq(
        mapping(
          "fileId" -> nonEmptyText,
          "displayName" -> nonEmptyText,
          "isSelected" -> boolean,
          "isFolder" -> boolean
        )(NodesToDisplay.apply)(NodesToDisplay.unapply)
      ),
      "selected" -> list(text),
      "allNodes" -> list(text),
      "pageSelected" -> number,
      "folderSelected" -> text,
      "returnToRoot" -> optional(text)
    )(NodesFormData.apply)(NodesFormData.unapply))

  def getPaginatedFiles(consignmentId: UUID,
                        pageNumber: Int,
                        limit: Option[Int],
                        selectedFolderId: UUID,
                        metadataType: String): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentPaginatedFile(consignmentId, pageNumber - 1, limit, selectedFolderId, request.token.bearerAccessToken)
      .map { paginatedFiles =>
        val totalPages = paginatedFiles.paginatedFiles.totalPages
          .getOrElse(throw new IllegalStateException(s"No 'total pages' returned for folderId $selectedFolderId"))
        val totalFiles = paginatedFiles.paginatedFiles.totalItems
          .getOrElse(throw new IllegalStateException(s"No 'total items' returned for folderId $selectedFolderId"))
        val parentFolder = paginatedFiles.parentFolder.
          getOrElse(throw new IllegalStateException(s"No 'parent folder' returned for consignment $consignmentId"))
        val edges: List[PaginatedFiles.Edges] = paginatedFiles.paginatedFiles.edges.getOrElse(List()).flatten
        val nodesToDisplay: List[NodesToDisplay] = generateNodesToDisplay(consignmentId, edges, selectedFolderId)
        val pageSize = limit.getOrElse(totalFiles)
        val resultsCount = if(nodesToDisplay.isEmpty) {
          ResultsCount(0, 0, 0)
        } else {
          ResultsCount(((pageNumber - 1) * pageSize) + 1, (pageNumber * pageSize).min(totalFiles), totalFiles)
        }
        Ok(views.html.standard.additionalMetadataNavigation(
          consignmentId,
          request.token.name,
          metadataType,
          parentFolder,
          totalPages,
          limit,
          pageNumber,
          selectedFolderId,
          paginatedFiles.parentFolderId,
          navigationForm.fill(NodesFormData(nodesToDisplay, selected = List(), allNodes = List(), pageNumber, selectedFolderId.toString,
            paginatedFiles.parentFolder)), resultsCount)
        ).uncache()
      }
  }

  def submit(consignmentId: UUID, limit: Option[Int], selectedFolderId: UUID, metadataType: String): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      val errorFunction: Form[NodesFormData] => Future[Result] = { formWithErrors: Form[NodesFormData] =>
        Future(Ok)
      }

      val successFunction: NodesFormData => Future[Result] = { formData: NodesFormData =>
        val selectedFiles: RedisSet[UUID, SynchronousResult] = cacheApi.set[UUID](consignmentId.toString)
        val pageSelected: Int = formData.pageSelected
        val folderSelected: String = formData.folderSelected
        selectedFiles.updateCache(formData, selectedFolderId)

        if (formData.returnToRoot.isDefined) {
          Future(Redirect(routes.AdditionalMetadataNavigationController
            .getPaginatedFiles(consignmentId, pageSelected, limit, UUID.fromString(formData.returnToRoot.get), metadataType)))
        } else {
          Future(Redirect(routes.AdditionalMetadataNavigationController
            .getPaginatedFiles(consignmentId, pageSelected, limit, UUID.fromString(folderSelected), metadataType)))
        }
      }

      val formValidationResult: Form[NodesFormData] = navigationForm.bindFromRequest()
      formValidationResult.fold(
        errorFunction,
        successFunction
      )
  }

  private def generateNodesToDisplay(consignmentId: UUID, edges: List[PaginatedFiles.Edges], selectedFolderId: UUID): List[NodesToDisplay] = {
    edges.map(edge => {
      val edgeNode = edge.node
      val selectedFiles = cacheApi.set[UUID](consignmentId.toString)
      val isSelected = selectedFiles.contains(edge.node.fileId) || selectedFiles.contains(selectedFolderId)
      val isFolder = edge.node.fileType.get == "Folder"
      NodesToDisplay(
        edgeNode.fileId.toString,
        edgeNode.fileName.get,
        isSelected,
        isFolder
      )
    })
  }
}
object AdditionalMetadataNavigationController {
  case class NodesFormData(nodesToDisplay: Seq[NodesToDisplay],
                           selected: List[String],
                           allNodes: List[String],
                           pageSelected: Int,
                           folderSelected: String,
                           returnToRoot: Option[String])

  case class NodesToDisplay(fileId: String, displayName: String, isSelected: Boolean = false, isFolder: Boolean = false)

  case class ResultsCount(startCount: Int, endCount: Int, total: Int)
}
