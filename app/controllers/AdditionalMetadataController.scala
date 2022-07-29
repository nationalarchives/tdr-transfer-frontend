package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.GetConsignmentPaginatedFiles.getConsignmentPaginatedFiles.GetConsignment.PaginatedFiles
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms.{boolean, list, mapping, nonEmptyText, number, seq, text}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.ConsignmentService

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class AdditionalMetadataController @Inject()(val consignmentService: ConsignmentService,
                                             val keycloakConfiguration: KeycloakConfiguration,
                                             val controllerComponents: SecurityComponents
                                            ) extends TokenSecurity {

  val navigationForm: Form[NodesFormData] = Form(
    mapping(
      "nodes" -> seq(
        mapping(
          "nodeIdStr" -> nonEmptyText,
          "displayName" -> nonEmptyText,
          "isSelected" -> boolean,
          "isFolder" -> boolean
        )(NodesToDisplay.apply)(NodesToDisplay.unapply)
      ),
      "previouslySelected" -> text,
      "selected" -> list(text),
      "allNodes" -> list(text),
      "pageSelected" -> number,
      "folderSelected" -> text
    )(NodesFormData.apply)(NodesFormData.unapply))

  def start(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignment <- consignmentService.getConsignmentDetails(consignmentId, request.token.bearerAccessToken)
      response <- consignment.parentFolder match {
        case Some(folder) =>
          Future(Ok(views.html.standard.additionalMetadataStart(folder, consignment.consignmentReference, consignmentId, request.token.name)))
        case None => Future.failed(new IllegalStateException("Parent folder not found"))
      }
    } yield response
  }

  def getPaginatedFiles(consignmentId: UUID, page: Int, limit: Option[Int], selectedFolderId: UUID): Action[AnyContent] = standardTypeAction(consignmentId)
  { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentPaginatedFile(consignmentId, page - 1, limit, selectedFolderId, request.token.bearerAccessToken)
      .map { paginatedFiles =>
        val totalPages = paginatedFiles.paginatedFiles.totalPages.get
        val parentFolder = paginatedFiles.parentFolder.get
        val previouslySelectedIds: String = ""
        val edges = paginatedFiles.paginatedFiles.edges.get.flatten
        val nodesToDisplay = generateNodesToDisplay(edges, Set())

        Ok(views.html.standard.additionalMetadata(
          consignmentId,
          request.token.name,
          parentFolder,
          totalPages,
          limit,
          page,
          selectedFolderId,
          navigationForm.fill(NodesFormData(nodesToDisplay, previouslySelectedIds, selected = List(), allNodes = List(), page, selectedFolderId.toString)))
        )
      }
  }

  def submit(consignmentId: UUID, page: Int, limit: Option[Int], selectedFolderId: UUID): Action[AnyContent] = standardTypeAction(consignmentId)
  { implicit request: Request[AnyContent] =>
    val errorFunction: Form[NodesFormData] => Future[Result]  = { formWithErrors: Form[NodesFormData] =>
      consignmentService.getConsignmentPaginatedFile(consignmentId, page - 1, limit, selectedFolderId, request.token.bearerAccessToken)
        .map { paginatedFiles =>
          Ok(views.html.standard.additionalMetadata(
            consignmentId,
            request.token.name,
            paginatedFiles.parentFolder.get,
            paginatedFiles.paginatedFiles.totalPages.get,
            limit,
            page,
            selectedFolderId,
            formWithErrors)
          )
        }
    }

    val successFunction: NodesFormData => Future[Result] = { formData: NodesFormData =>
      val pageToGo = formData.pageSelected
      val folderSelected = formData.folderSelected
      //PageToGo -1 so that the pagination number matches that of the page in the url
      consignmentService.getConsignmentPaginatedFile(consignmentId, pageToGo -1, limit, UUID.fromString(folderSelected), request.token.bearerAccessToken)
        .map { paginatedFiles =>
          val edges: List[PaginatedFiles.Edges] = paginatedFiles.paginatedFiles.edges.get.flatten
          val selected = formData.selected
          val previouslySelectedIds: String = formData.previouslySelected + "," + selected.mkString(",")
          val updatedSelection = handleDeselection(selected, formData.allNodes, previouslySelectedIds)
          val nodesToDisplay = generateNodesToDisplay(edges, updatedSelection)

          Ok(views.html.standard.additionalMetadata(
            consignmentId,
            request.token.name,
            paginatedFiles.parentFolder.get,
            paginatedFiles.paginatedFiles.totalPages.get,
            limit,
            pageToGo,
            selectedFolderId,
            navigationForm.fill(NodesFormData(nodesToDisplay, updatedSelection.mkString(","), List(), List(), pageToGo, folderSelected)))
          )
        }
    }

    val formValidationResult: Form[NodesFormData] = navigationForm.bindFromRequest()
    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }

  private def handleDeselection(selected: List[String], allNodes: List[String], previouslySelectedIds: String): Set[String] = {
    //Filter out selected Nodes
    val allNodesWithoutSelection = allNodes.filter(x => !selected.contains(x)).mkString(",")
    //Remove the Filtered Nodes from the PreviouslySelected
    previouslySelectedIds.split(",").toSet.filter(previouslySelectedId => !allNodesWithoutSelection.contains(previouslySelectedId))
  }

  private def generateNodesToDisplay(edges: List[PaginatedFiles.Edges], ids: Set[String]): List[NodesToDisplay] = {
    edges.map(edge => {
      val isSelected = ids.contains(edge.node.fileId.toString)
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
                          previouslySelected: String,
                          selected: List[String],
                          allNodes: List[String],
                          pageSelected: Int,
                          folderSelected: String)

case class NodesToDisplay(nodeIdStr: String, displayName: String, isSelected: Boolean = false, isFolder: Boolean = false)
