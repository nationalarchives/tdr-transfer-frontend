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
import play.api.data.Form
import play.api.data.Forms.{boolean, list, mapping, nonEmptyText, seq, text}
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
          "isSelected" -> boolean
        )(NodesToDisplay.apply)(NodesToDisplay.unapply)
      ),
      "previouslySelected" -> text,
      "selected" -> list(text)
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
          navigationForm.fill(NodesFormData(edges.map(edge =>
            NodesToDisplay(edge.node.fileId.toString, edge.node.fileName.get)), previouslySelectedIds, selected = List())))
        )
      }
  }
  //scalastyle:off
  def submit(consignmentId: UUID, page: Int, limit: Option[Int], selectedFolderId: UUID): Action[AnyContent] = standardTypeAction(consignmentId)
  { implicit request: Request[AnyContent] =>
    val ids = Seq(6, 7, 8, 9)
    val errorFunction: Form[NodesFormData] => Future[Result]  = { formWithErrors: Form[NodesFormData] =>
//      Ok(views.html.standard.additionalMetadata(navigationForm.fill(NodesFormData(ids.map(id => NodesToDisplay(id.toString, s"ID $id")), "", List()))))
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
      println(formData)
      consignmentService.getConsignmentPaginatedFile(consignmentId, page - 1, limit, selectedFolderId, request.token.bearerAccessToken)
        .map { paginatedFiles =>
          val selected: String = formData.selected.mkString(",")
          println("Current Selection: " + selected)
          val previouslySelectedIds: String = formData.previouslySelected + selected + ","
          val edges = paginatedFiles.paginatedFiles.edges.get.flatten
          println("All Selection: " + previouslySelectedIds)

          //      Ok(views.html.standard.additionalMetadata(navigationForm.
          //      fill(NodesFormData(ids.map(id => NodesToDisplay(id.toString, s"ID $id")), previouslySelectedIds, List()))))
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
            navigationForm.fill(NodesFormData(edges.map(edge =>
              NodesToDisplay(edge.node.fileId.toString, edge.node.fileName.get)), previouslySelectedIds, List())))
          )
        }
    }

    val formValidationResult: Form[NodesFormData] = navigationForm.bindFromRequest()
    formValidationResult.fold(
      errorFunction,
      successFunction
    )
  }
}

case class NodesFormData(nodesToDisplay: Seq[NodesToDisplay], previouslySelected: String, selected: List[String])
case class NodesToDisplay(nodeIdStr: String, displayName: String, isSelected: Boolean = false)
