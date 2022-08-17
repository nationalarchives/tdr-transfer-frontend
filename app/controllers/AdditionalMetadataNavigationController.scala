package controllers

import auth.TokenSecurity
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.KeycloakConfiguration
import graphql.codegen.GetAllDescendants
import graphql.codegen.GetConsignmentPaginatedFiles.getConsignmentPaginatedFiles.GetConsignment.PaginatedFiles
import org.pac4j.play.scala.SecurityComponents
import play.api.cache.redis.{CacheApi, RedisMap, RedisSet, SynchronousResult}
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

    def updateCache(formData: NodesFormData, selectedFolderId: UUID, consignmentId: UUID, token: BearerAccessToken): Unit = {
      val currentFolder: String = formData.folderSelected
      val currentFolderId = UUID.fromString(currentFolder)
      val allNodes: List[String] = formData.allNodes
      val selectedNodes: List[String] = formData.selected
      val deselectedNodes: List[String] = allNodes.filter(id => !selectedNodes.contains(id))

      //Handle folder deselection separately, as has three potential states: deselected, partially deselected, selected
      val deselectedIdsExceptFolder = deselectedNodes.filter(_ != currentFolder).map(UUID.fromString(_)).toSet
      val selectedIds = selectedNodes.map(UUID.fromString(_)).toSet
      val folderDescendantsCache = cacheApi.map[List[String]](s"${consignmentId}_folders")

      if (!folderDescendantsCache.contains(currentFolder)) {
        consignmentService.getAllDescendants(consignmentId, Set(currentFolderId), token).map {
          data =>
            val allFolderIds = data.map(_.fileId.toString)
            folderDescendantsCache.add(currentFolder, allFolderIds)
        }
      }

      for {
        allSelectedDescendants <- consignmentService.getAllDescendants(consignmentId, selectedIds, token)
        allDeselectedDescendantsExceptFolder <- consignmentService.getAllDescendants(consignmentId, deselectedIdsExceptFolder, token)
      } yield {

        val allFolder = folderDescendantsCache.get(currentFolder).getOrElse(List())
        val allSelected = allSelectedDescendants.map(_.fileId.toString)
        val allDeselectedExceptFolder = allDeselectedDescendantsExceptFolder.map(_.fileId.toString)

        cache.addSelectedIds(allSelected)
        cache.removedDeselectedIds(allDeselectedExceptFolder)

        val allFolderChildren = allFolder.filter(_ != currentFolder)
        val allFolderChildrenUUIDs = allFolderChildren.map(UUID.fromString(_))

        //Folder has been actively deselected
        if (deselectedNodes.contains(currentFolder) && cache.containsAll(allFolder.map(UUID.fromString(_)))) {
          cache.removedDeselectedIds(allFolder)
        }

        //All folder descendents have been selected, set folder to selected
        if (cache.containsAll(allFolderChildrenUUIDs)) {
          cache.add(currentFolderId)
          //Some not all folder descendants have been selected, set folder to partially selected
        } else if (cache.containsSome(allFolderChildrenUUIDs)) {
          cache.remove(currentFolderId)
        }
      }
    }

    def addSelectedIds(elems: List[String]): Unit = {
      elems.foreach(id => cache.add(UUID.fromString(id)))
    }

    def removedDeselectedIds(deselectedFiles: List[String]): Unit = {
      deselectedFiles.foreach(id => if (cache.contains(UUID.fromString(id))) cache.remove(UUID.fromString(id)))
    }

    def containsAll(ids: List[UUID]): Boolean = {
      ids.forall(cache.contains(_))
    }

    def containsSome(ids: List[UUID]): Boolean = {
      ids.exists(cache.contains(_)) && !containsAll(ids)
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
                        selectedFolderId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentPaginatedFile(consignmentId, pageNumber - 1, limit, selectedFolderId, request.token.bearerAccessToken)
      .map { paginatedFiles =>

        val totalPages = paginatedFiles.paginatedFiles.totalPages
          .getOrElse(throw new IllegalStateException(s"No 'total pages' returned for folderId $selectedFolderId"))
        val parentFolder = paginatedFiles.parentFolder.
          getOrElse(throw new IllegalStateException(s"No 'parent folder' returned for consignment $consignmentId"))
        val edges: List[PaginatedFiles.Edges] = paginatedFiles.paginatedFiles.edges.getOrElse(List()).flatten
        val nodesToDisplay: List[NodesToDisplay] = generateNodesToDisplay(consignmentId, edges, selectedFolderId)
        Ok(views.html.standard.additionalMetadataNavigation(
          consignmentId,
          request.token.name,
          parentFolder,
          totalPages,
          limit,
          pageNumber,
          selectedFolderId,
          paginatedFiles.parentFolderId,
          navigationForm.fill(NodesFormData(nodesToDisplay, selected = List(), allNodes = List(), pageNumber, selectedFolderId.toString,
            paginatedFiles.parentFolder)))
        ).uncache()
      }
  }

  def submit(consignmentId: UUID, limit: Option[Int], selectedFolderId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) {
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
            .getPaginatedFiles(consignmentId, pageSelected, limit, UUID.fromString(formData.returnToRoot.get))))
        } else {
          Future(Redirect(routes.AdditionalMetadataNavigationController
            .getPaginatedFiles(consignmentId, pageSelected, limit, UUID.fromString(folderSelected))))
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

      val isFolder = edge.node.fileType.get == "Folder"
      val isPartiallySelected = isFolder && folderPartiallySelected(consignmentId, edge.node.fileId)
      val isSelected = !isPartiallySelected && selectedFiles.contains(edge.node.fileId)
      NodesToDisplay(
        edgeNode.fileId.toString,
        edgeNode.fileName.get,
        isSelected,
        isFolder,
        isPartiallySelected
      )
    })
  }

  private def folderPartiallySelected(consignmentId: UUID, folderId: UUID): Boolean = {
    val selectedFiles = cacheApi.set[UUID](consignmentId.toString)
    val allFolderDescendants = cacheApi.map[List[String]](s"${consignmentId}_folders")

    val folderIds = allFolderDescendants.getFields(folderId.toString)
      .flatMap(_.getOrElse(List())).map(UUID.fromString(_)).toList

    selectedFiles.containsSome(folderIds)
  }
}

case class NodesFormData(nodesToDisplay: Seq[NodesToDisplay],
                         selected: List[String],
                         allNodes: List[String],
                         pageSelected: Int,
                         folderSelected: String,
                         returnToRoot: Option[String])

case class NodesToDisplay(fileId: String,
                          displayName: String,
                          isSelected: Boolean = false,
                          isFolder: Boolean = false,
                          isPartiallySelected: Boolean = false)
