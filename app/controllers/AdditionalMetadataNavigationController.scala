package controllers

import auth.TokenSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.KeycloakConfiguration
import controllers.AdditionalMetadataNavigationController._
import graphql.codegen.GetAllDescendants.getAllDescendantIds.AllDescendants
import graphql.codegen.GetConsignmentPaginatedFiles.getConsignmentPaginatedFiles.GetConsignment.PaginatedFiles
import org.pac4j.play.scala.SecurityComponents
import play.api.cache.redis.{CacheApi, RedisMap, RedisSet, SynchronousResult}
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

  implicit class DescendantsHelper(descendants: List[AllDescendants]) {
    def toDescendantFileIds: List[UUID] = {
      descendants.filter(_.fileType.contains("File")).map(_.fileId)
    }
  }

  implicit class CacheMapHelper(cache: RedisMap[List[UUID], SynchronousResult]) {
    def getDescendantFileIds(consignmentId: UUID, folderId: UUID, token: BearerAccessToken): Future[List[UUID]] = {
      for {
        _ <- if (!cache.contains(folderId.toString)) {
          consignmentService.getAllDescendants(consignmentId, Set(folderId), token).map {
            data => cache.add(folderId.toString, data.toDescendantFileIds)
          }
        } else { Future.successful(()) }
      } yield { cache.get(folderId.toString).getOrElse(List()) }
    }
  }

  implicit class CacheSetHelper(cache: RedisSet[UUID, SynchronousResult]) {
    def updateCache(formData: NodesFormData, consignmentId: UUID, token: BearerAccessToken): Future[Unit] = {
      val partSelectedCache = cacheApi.set[UUID](s"${consignmentId}_partSelected")
      val folderDescendantsCache = cacheApi.map[List[UUID]](s"${consignmentId}_folders")
      val currentFolder: String = formData.folderSelected
      val currentFolderId = UUID.fromString(currentFolder)
      val allDisplayedNodesIds: List[UUID] = formData.allNodes.toUuids
      val selectedNodeIds: List[UUID] = formData.selected.toUuids
      val deselectedNodeIds: List[UUID] = allDisplayedNodesIds
        .filter(id => !selectedNodeIds.contains(id)).filter(id => !partSelectedCache.contains(id))

      //Handle folder deselection separately, as has three potential states: deselected, partially deselected, descendantFilesSelected
      val deselectedIdsExceptFolder: Set[UUID] = deselectedNodeIds.filter(_ != currentFolderId).toSet

      for {
        allSelectedIds <- consignmentService.getAllDescendants(consignmentId, selectedNodeIds.toSet, token)
        allDeselectedExceptFolderIds <- consignmentService.getAllDescendants(consignmentId, deselectedIdsExceptFolder, token)
        allFolderIds <- folderDescendantsCache.getDescendantFileIds(consignmentId, currentFolderId, token)
      } yield {
        partSelectedCache.remove(currentFolderId)
        cache.addSelectedIds(allSelectedIds.toDescendantFileIds)
        cache.removedDeselectedIds(allDeselectedExceptFolderIds.toDescendantFileIds)

        //Folder has been actively deselected
        if (deselectedNodeIds.contains(currentFolderId) && cache.containsAll(allFolderIds)) {
          cache.removedDeselectedIds(allFolderIds)
        }

        if (cache.containsSome(allFolderIds)) {
          partSelectedCache.add(currentFolderId)
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
      if (elems.isEmpty) { false } else { elems.forall(cache.contains) }
    }

    def containsSome(elems: List[UUID]): Boolean = {
      if (elems.isEmpty) { false } else { elems.exists(cache.contains) && !containsAll(elems) }
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
          "descendantFilesSelected" -> number
        )(NodesToDisplay.apply)(NodesToDisplay.unapply)
      ),
      "descendantFilesSelected" -> list(text),
      "allNodes" -> list(text),
      "pageSelected" -> number,
      "folderSelected" -> text,
      "returnToRoot" -> optional(text),
      "addClosureProperties" -> optional(text)
    )(NodesFormData.apply)(NodesFormData.unapply))

  def getPaginatedFiles(consignmentId: UUID, pageNumber: Int, limit: Option[Int], selectedFolderId: UUID,
                        metadataType: String): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      paginatedFiles <- consignmentService.getConsignmentPaginatedFile(consignmentId, pageNumber - 1, limit, selectedFolderId, request.token.bearerAccessToken)
      edges = paginatedFiles.paginatedFiles.edges.getOrElse(List()).flatten
      nodesToDisplay <- generateNodesToDisplay(consignmentId, edges, request.token.bearerAccessToken)
    } yield {
      val totalPages = paginatedFiles.paginatedFiles.totalPages
        .getOrElse(throw new IllegalStateException(s"No 'total pages' returned for folderId $selectedFolderId"))
      val totalFiles = paginatedFiles.paginatedFiles.totalItems
        .getOrElse(throw new IllegalStateException(s"No 'total items' returned for folderId $selectedFolderId"))
      val parentFolder = paginatedFiles.parentFolder.
        getOrElse(throw new IllegalStateException(s"No 'parent folder' returned for consignment $consignmentId"))
      val pageSize = limit.getOrElse(totalFiles)
      val resultsCount = if (nodesToDisplay.isEmpty) {
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
          paginatedFiles.parentFolder, None)), resultsCount)
      ).uncache()
    }
  }

  def submit(consignmentId: UUID, limit: Option[Int], selectedFolderId: UUID, metadataType: String): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      val errorFunction: Form[NodesFormData] => Future[Result] = { _: Form[NodesFormData] =>
        Future(Redirect(routes.AdditionalMetadataNavigationController
          .getPaginatedFiles(consignmentId, 1, limit, selectedFolderId, metadataType)))
      }

      val successFunction: NodesFormData => Future[Result] = { formData: NodesFormData =>
        val selectedFiles: RedisSet[UUID, SynchronousResult] = cacheApi.set[UUID](consignmentId.toString)
        val pageSelected: Int = formData.pageSelected
        val folderSelected: String = formData.folderSelected
        selectedFiles.updateCache(formData, consignmentId, request.token.bearerAccessToken).map(_ => {
          val folderId = UUID.fromString(formData.returnToRoot.getOrElse(folderSelected))
          if(formData.addClosureProperties.isDefined) {
            Redirect(routes.AddClosureMetadataController.addClosureMetadata(consignmentId, selectedFiles.toSet.toList))
          } else {
            Redirect(routes.AdditionalMetadataNavigationController
              .getPaginatedFiles(consignmentId, pageSelected, limit, folderId, metadataType))
          }

        })
      }

      val formValidationResult: Form[NodesFormData] = navigationForm.bindFromRequest()
      formValidationResult.fold(
        errorFunction,
        successFunction
      )
  }

  private def generateNodesToDisplay(consignmentId: UUID,
                                     edges: List[PaginatedFiles.Edges],
                                     token: BearerAccessToken): Future[List[NodesToDisplay]] = {
    val nodes = edges.map(edge => {
      val edgeNode = edge.node
      val edgeNodeId = edgeNode.fileId
      val isFolder = edgeNode.fileType.contains("Folder")

      for {
        descendantFilesSelected <- if (isFolder) { getDescendantFilesSelected(consignmentId, edgeNodeId, token) } else { Future(0) }
        isSelected <- selected(consignmentId, edgeNodeId, isFolder, token)
      } yield {
        NodesToDisplay(
          edgeNodeId.toString,
          edgeNode.fileName.getOrElse(""),
          isSelected,
          isFolder,
          descendantFilesSelected
        )}
    })
    Future.sequence(nodes)
  }

  private def selected(consignmentId: UUID, nodeId: UUID, isFolder: Boolean, token: BearerAccessToken): Future[Boolean] = {
    val folderDescendantsCache = cacheApi.map[List[UUID]](s"${consignmentId}_folders")
    val selectedFiles: RedisSet[UUID, SynchronousResult] = cacheApi.set[UUID](consignmentId.toString)

    for {
      ids <- if (isFolder) {
        folderDescendantsCache.getDescendantFileIds(consignmentId, nodeId, token)
      } else { Future(List(nodeId)) }
    } yield selectedFiles.containsAll(ids)
  }

  private def getDescendantFilesSelected(consignmentId: UUID,
                                folderId: UUID,
                                token: BearerAccessToken): Future[Int] = {
    val selectedFiles: RedisSet[UUID, SynchronousResult] = cacheApi.set[UUID](consignmentId.toString)
    val partSelectedCache: RedisSet[UUID, SynchronousResult] = cacheApi.set[UUID](s"${consignmentId}_partSelected")
    val folderDescendantsCache = cacheApi.map[List[UUID]](s"${consignmentId}_folders")

    for {
      ids <- folderDescendantsCache.getDescendantFileIds(consignmentId, folderId, token)
      _ = if (selectedFiles.containsSome(ids)) partSelectedCache.add(folderId)
      descendantFilesSelected = selectedFiles.toSet.intersect(ids.toSet).size
    } yield descendantFilesSelected
  }
}

object AdditionalMetadataNavigationController {

  case class NodesFormData(nodesToDisplay: Seq[NodesToDisplay],
                           selected: List[String],
                           allNodes: List[String],
                           pageSelected: Int,
                           folderSelected: String,
                           returnToRoot: Option[String],
                           addClosureProperties: Option[String])

  case class NodesToDisplay(fileId: String,
                            displayName: String,
                            isSelected: Boolean = false,
                            isFolder: Boolean = false,
                            descendantFilesSelected: Int = 0)

  case class ResultsCount(startCount: Int, endCount: Int, total: Int)
}
