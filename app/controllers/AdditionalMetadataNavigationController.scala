package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
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

  def getPaginatedFiles(consignmentId: UUID, page: Int, limit: Option[Int], selectedFolderId: UUID): Action[AnyContent] = standardTypeAction(consignmentId)
  { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentPaginatedFile(consignmentId, page - 1, limit, selectedFolderId, request.token.bearerAccessToken)
      .map { paginatedFiles =>
        val totalPages = paginatedFiles.paginatedFiles.totalPages.get
        val parentFolder = paginatedFiles.parentFolder.get
        val edges = paginatedFiles.paginatedFiles.edges.get.flatten
        val nodesToDisplay: List[NodesToDisplay] = generateNodesToDisplay(consignmentId, edges, selectedFolderId)
        Ok(views.html.standard.additionalMetadataNavigation(
          consignmentId,
          request.token.name,
          parentFolder,
          totalPages,
          limit,
          page,
          selectedFolderId,
          paginatedFiles.parentFolderId,
          navigationForm.fill(NodesFormData(nodesToDisplay, selected = List(), allNodes = List(), page, selectedFolderId.toString,
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
        formData.selected.foreach(ids => selectedFiles.add(UUID.fromString(ids)))
        //Handle deselected Items
        val deselectedFiles = handleDeselection(selectedFiles, formData)
        //Handle subfolder child deselection
        if (deselectedFiles.nonEmpty && selectedFiles.contains(selectedFolderId)) selectedFiles.remove(selectedFolderId)
        //Pass the parent id instead of folderId, if the 'return to root' button is clicked
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

  private def handleDeselection(selectedFiles: RedisSet[UUID, SynchronousResult], formData: NodesFormData): List[String] = {
    val allNodes: List[String] = formData.allNodes
    val selected: List[String] = formData.selected
    val deselectedFiles: List[String] = allNodes.filter(ids => !selected.contains(ids))
    deselectedFiles.foreach(ids => if (selectedFiles.contains(UUID.fromString(ids))) selectedFiles.remove(UUID.fromString(ids)))
    deselectedFiles
  }

  private def generateNodesToDisplay(consignmentId: UUID, edges: List[PaginatedFiles.Edges], selectedFolderId: UUID): List[NodesToDisplay] = {
    edges.map(edge => {
      val selectedFiles = cacheApi.set[UUID](consignmentId.toString)
      val isSelected = selectedFiles.contains(edge.node.fileId) || selectedFiles.contains(selectedFolderId)
      val isFolder = edge.node.fileType.get == "Folder"
      NodesToDisplay(
        edge.node.fileId.toString,
        edge.node.fileName.get,
        isSelected,
        isFolder
      )
    })
  }
}

case class NodesFormData(nodesToDisplay: Seq[NodesToDisplay],
                         selected: List[String],
                         allNodes: List[String],
                         pageSelected: Int,
                         folderSelected: String,
                         returnToRoot: Option[String])

case class NodesToDisplay(fileId: String, displayName: String, isSelected: Boolean = false, isFolder: Boolean = false)
