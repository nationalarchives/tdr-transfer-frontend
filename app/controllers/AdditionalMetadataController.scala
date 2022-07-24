package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.GetConsignmentPaginatedFiles.getConsignmentPaginatedFiles.GetConsignment.PaginatedFiles
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
import play.api.data.Form
import play.api.data.Forms.{boolean, list, mapping, nonEmptyText, number, seq, text}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.ConsignmentService

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AdditionalMetadataController @Inject()(val controllerComponents: SecurityComponents,
                                             val keycloakConfiguration: KeycloakConfiguration,
                                             val consignmentService: ConsignmentService)
                                            (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

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

  def getPaginatedFiles(consignmentId: UUID, page: Int, limit: Option[Int], selectedFolderId: UUID): Action[AnyContent] = standardTypeAction(consignmentId)
  { implicit request: Request[AnyContent] =>
    consignmentService.getConsignmentPaginatedFile(consignmentId, page - 1, limit, selectedFolderId, request.token.bearerAccessToken)
      .map { paginatedFiles =>
        val totalFiles = paginatedFiles.totalFiles
        val totalPages = paginatedFiles.paginatedFiles.totalPages.get
        val parentFolder = paginatedFiles.parentFolder.get

        val previouslySelectedIds: String = ""

        val edges = paginatedFiles.paginatedFiles.edges.get.flatten

        val nodesToDisplay = generateNodesToDisplay(edges, Set())

        Ok(views.html.standard.additionalMetadata(
          consignmentId,
          "consignmentRef", //Use An actual reference
          request.token.name,
          parentFolder,
          totalFiles,
          totalPages,
          limit,
          page,
          selectedFolderId,
          paginatedFiles.paginatedFiles.edges.get.flatten,
          navigationForm.fill(NodesFormData(nodesToDisplay, previouslySelectedIds, selected = List(), List(), page, selectedFolderId.toString)))
        )
      }
  }
  //scalastyle:off
  def submit(consignmentId: UUID, page: Int, limit: Option[Int], selectedFolderId: UUID): Action[AnyContent] = standardTypeAction(consignmentId)
  { implicit request: Request[AnyContent] =>
    val errorFunction: Form[NodesFormData] => Future[Result]  = { formWithErrors: Form[NodesFormData] =>
      println(formWithErrors.value.get.folderSelected)
      println("ERROR")
      consignmentService.getConsignmentPaginatedFile(consignmentId, page - 1, limit, selectedFolderId, request.token.bearerAccessToken)
        .map { paginatedFiles =>
          Ok(views.html.standard.additionalMetadata(
            consignmentId,
            "consignmentReference",
            request.token.name,
            paginatedFiles.parentFolder.get,
            paginatedFiles.totalFiles,
            paginatedFiles.paginatedFiles.totalPages.get,
            limit,
            page, selectedFolderId,
            paginatedFiles.paginatedFiles.edges.get.flatten,
            formWithErrors)
          )
        }
    }

    val successFunction: NodesFormData => Future[Result] = { formData: NodesFormData =>
      val pageToGo = formData.pageSelected
      println("Page to Go: " + pageToGo)
      val folderSelected = formData.folderSelected
      consignmentService.getConsignmentPaginatedFile(consignmentId, pageToGo -1, limit, UUID.fromString(folderSelected), request.token.bearerAccessToken)
        .map { paginatedFiles =>
          val edges: List[PaginatedFiles.Edges] = paginatedFiles.paginatedFiles.edges.get.flatten

          val selected = formData.selected.mkString(",")
          println("Current Selection:" + selected)
          val allNodes = formData.allNodes//.mkString(",")
          println("All Nodes:" + allNodes)

          //compare the selected node ids against allNodes and filter out the selected nodes from that list
//          val removeSelected = allNodes.filter(x => !selected.contains(x))
//          println("removeSelected: " + removeSelected)
          //remove all the filtered nodes from the previously selected ids / add the selected nodes

          //Add the selected to the previouslySelected

          val previouslySelectedIds: String = formData.previouslySelected + "," + selected
          println("All Selection: " + previouslySelectedIds)

          //Filter out selected Nodes
          val removeSelected = allNodes.filter(x => !selected.contains(x)).mkString(",")
          println("remove selected:" + removeSelected)

          //Remove the Filtered Nodes from the PreviouslySelected
          val removeFrom = previouslySelectedIds.split(",").toSet.filter(x => !removeSelected.contains(x))
          println("Remove Filtered Nodes from previously selected:"+removeFrom)
          //Add selected Nodes

          val ids: Set[String] = previouslySelectedIds.split(",").toSet.filter(id => id.nonEmpty)
/*          println("Total Selection: " + ids.size)
          ids.foreach(println)*/

          val nodesToDisplay = generateNodesToDisplay(edges, removeFrom)


          println("*******************")

          Ok(views.html.standard.additionalMetadata(
            consignmentId,
            "consignmentReference",
            request.token.name,
            paginatedFiles.parentFolder.get,
            paginatedFiles.totalFiles,
            paginatedFiles.paginatedFiles.totalPages.get,
            limit,
            pageToGo,
            selectedFolderId,
            paginatedFiles.paginatedFiles.edges.get.flatten,
            navigationForm.fill(NodesFormData(nodesToDisplay, removeFrom.mkString(","), List(), List(), pageToGo, folderSelected)))
          )
        }
    }

    val formValidationResult: Form[NodesFormData] = navigationForm.bindFromRequest()
    formValidationResult.fold(
      errorFunction,
      successFunction
    )
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

case class NodesFormData(nodesToDisplay: Seq[NodesToDisplay], previouslySelected: String, selected: List[String], allNodes: List[String], pageSelected: Int, folderSelected: String)
case class NodesToDisplay(nodeIdStr: String, displayName: String, isSelected: Boolean = false, isFolder: Boolean = false)
