package controllers

import auth.TokenSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.KeycloakConfiguration
import controllers.AdditionalMetadataNavigationController.{NodesFormData, NodesToDisplay, ResultsCount}
import graphql.codegen.GetConsignmentPaginatedFiles.getConsignmentPaginatedFiles.GetConsignment.PaginatedFiles
import org.pac4j.play.scala.SecurityComponents
import play.api.cache.redis.{CacheApi, RedisSet, SynchronousResult}
import play.api.data.Form
import play.api.data.Forms._
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

  implicit class ListHelper(list: List[String]) {
    def toUuids: List[UUID] = {
      list.map(UUID.fromString)
    }
  }

  implicit class CacheHelper(cache: RedisSet[UUID, SynchronousResult]) {

    def updateCache(formData: NodesFormData, selectedFolderId: UUID, consignmentId: UUID, token: BearerAccessToken): Unit = {
      val currentFolder: String = formData.folderSelected
      val currentFolderId = UUID.fromString(currentFolder)
      val allNodeIds: List[UUID] = formData.allNodes.toUuids
      val selectedNodeIds: List[UUID] = formData.selected.toUuids
      val deselectedNodeIds: List[UUID] = allNodeIds.filter(id => !selectedNodeIds.contains(id))

      //Handle folder deselection separately, as has three potential states: deselected, partially deselected, selected
      val deselectedIdsExceptFolder: Set[UUID] = deselectedNodeIds.filter(_ != currentFolderId).toSet
      val folderDescendantsCache = cacheApi.map[List[UUID]](s"${consignmentId}_folders")

      if (!folderDescendantsCache.contains(currentFolder)) {
        consignmentService.getAllDescendants(consignmentId, Set(currentFolderId), token).map {
          data =>
            val allFolderIds = data.map(_.fileId)
            folderDescendantsCache.add(currentFolder, allFolderIds)
        }
      }

      for {
        allSelectedDescendants <- consignmentService.getAllDescendants(consignmentId, selectedNodeIds.toSet, token)
        allDeselectedDescendantsExceptFolder <- consignmentService.getAllDescendants(consignmentId, deselectedIdsExceptFolder, token)
      } yield {

        val allFolderIds: List[UUID] = folderDescendantsCache.get(currentFolder).getOrElse(List())
        val allSelectedIds: List[UUID] = allSelectedDescendants.map(_.fileId)
        val allDeselectedExceptFolderIds: List[UUID] = allDeselectedDescendantsExceptFolder.map(_.fileId)

        cache.addSelectedIds(allSelectedIds)
        cache.removedDeselectedIds(allDeselectedExceptFolderIds)

        val allFolderChildrenIds = allFolderIds.filter(_ != currentFolderId)

        //Folder has been actively deselected
        if (deselectedNodeIds.contains(currentFolderId) && cache.containsAll(allFolderIds)) {
          cache.removedDeselectedIds(allFolderIds)
        }

        //All folder descendents have been selected, set folder to selected
        if (cache.containsAll(allFolderChildrenIds)) {
          cache.add(currentFolderId)
          //Some not all folder descendants have been selected, set folder to partially selected
        } else if (cache.containsSome(allFolderChildrenIds)) {
          cache.remove(currentFolderId)
        }
      }
    }

    def addSelectedIds(elems: List[UUID]): Unit = {
      elems.foreach(cache.add(_))
    }

    def removedDeselectedIds(deselectedElems: List[UUID]): Unit = {
      deselectedElems.foreach(id => if (cache.contains(id)) cache.remove(id))
    }

    def containsAll(elems: List[UUID]): Boolean = {
      elems.forall(cache.contains)
    }

    def containsSome(elems: List[UUID]): Boolean = {
      elems.exists(cache.contains) && !containsAll(elems)
    }
  }

  val navigationForm: Form[NodesFormData] = Form(
    mapping(
      "nodes" -> seq(
        mapping(
          "fileId" -> nonEmptyText,
          "displayName" -> nonEmptyText,
          "isSelected" -> boolean,
          "isFolder" -> boolean,
          "isPartiallySelected" -> boolean
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
        val resultsCount = ResultsCount(((pageNumber - 1) * pageSize) + 1, (pageNumber * pageSize).min(totalFiles), totalFiles)
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
        selectedFiles.updateCache(formData, selectedFolderId, consignmentId, request.token.bearerAccessToken)

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
      val edgeNodeId = edgeNode.fileId
      val selectedFiles = cacheApi.set[UUID](consignmentId.toString)

      val isFolder = edgeNode.fileType.get == "Folder"
      val isPartiallySelected = isFolder && folderPartiallySelected(consignmentId, edgeNodeId)
      val isSelected = !isPartiallySelected && selectedFiles.contains(edgeNodeId)
      NodesToDisplay(
        edgeNodeId.toString,
        edgeNode.fileName.get,
        isSelected,
        isFolder,
        isPartiallySelected
      )
    })
  }

  private def folderPartiallySelected(consignmentId: UUID, folderId: UUID): Boolean = {
    val selectedFiles = cacheApi.set[UUID](consignmentId.toString)
    val allFolderDescendants = cacheApi.map[List[UUID]](s"${consignmentId}_folders")

    val folderIds = allFolderDescendants.getFields(folderId.toString)
      .flatMap(_.getOrElse(List())).toList

    selectedFiles.containsSome(folderIds)
  }
}
object AdditionalMetadataNavigationController {
  case class NodesFormData(nodesToDisplay: Seq[NodesToDisplay],
                           selected: List[String],
                           allNodes: List[String],
                           pageSelected: Int,
                           folderSelected: String,
                           returnToRoot: Option[String])

  case class NodesToDisplay(fileId: String, displayName: String, isSelected: Boolean = false, isFolder: Boolean = false, isPartiallySelected = false)

  case class ResultsCount(startCount: Int, endCount: Int, total: Int)
}
