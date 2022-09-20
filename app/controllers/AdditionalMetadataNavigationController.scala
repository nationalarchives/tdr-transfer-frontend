package controllers

import auth.TokenSecurity
import cats.implicits.toTraverseOps
import controllers.util.NaturalSorting._
import controllers.AddClosureMetadataController
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.KeycloakConfiguration
import controllers.AdditionalMetadataNavigationController._
import graphql.codegen.GetAllDescendants.getAllDescendantIds.AllDescendants
import graphql.codegen.GetConsignmentPaginatedFiles.getConsignmentPaginatedFiles.GetConsignment.PaginatedFiles
import graphql.codegen.GetFilesForNavigation.getFilesForNavigation.GetConsignment.Files
import org.pac4j.play.scala.SecurityComponents
import play.api.cache.redis.{CacheApi, RedisMap, RedisSet, SynchronousResult}
import play.api.data.{Form, FormError}
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.ConsignmentService
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.Inject
import scala.annotation.tailrec
import scala.concurrent.Future

class AdditionalMetadataNavigationController @Inject()(val consignmentService: ConsignmentService,
                                                       val keycloakConfiguration: KeycloakConfiguration,
                                                       val controllerComponents: SecurityComponents,
                                                       val cacheApi: CacheApi
                                                      ) extends TokenSecurity {



  val navigationForm: Form[NodesFormData] = Form(
    mapping(
      "fileIds" -> list(text),
    )(NodesFormData.apply)(NodesFormData.unapply))

  def getAllFiles(consignmentId: java.util.UUID, metadataType: String): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService.getAllFiles(consignmentId, request.token.bearerAccessToken).map(consignment => {
      val files: List[Files] = consignment.getConsignment.toList.flatMap(_.files)
      val filesMap = files.groupBy(_.parentId).filter(_._1.nonEmpty).map {case (k, v)  => k.get -> v}
      val parentId = files.find(_.parentId.isEmpty).map(_.fileId).getOrElse(throw new Exception("missing parent id"))

      Ok(views.html.standard.additionalMetadataNavigation(consignmentId, request.token.name, metadataType, filesMap, parentId))
    })
  }

  def submit(consignmentId: UUID, metadataType: String): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      val errorFunction: Form[NodesFormData] => Future[Result] = { formWithErrors: Form[NodesFormData] =>
        Future(Ok)
      }

      val successFunction: NodesFormData => Future[Result] = { formData: NodesFormData =>
        Future(Redirect(routes.AddClosureMetadataController.addClosureMetadata(consignmentId, formData.fileIds)))
      }

      val formValidationResult: Form[NodesFormData] = navigationForm.bindFromRequest()
      formValidationResult.fold(
        errorFunction,
        successFunction
      )
  }


}

object AdditionalMetadataNavigationController {



  case class NodesFormData(fileIds: List[String])

  case class NodesToDisplay(fileId: String,
                            displayName: String,
                            isSelected: Boolean = false,
                            isFolder: Boolean = false,
                            descendantFilesSelected: Int = 0)

}
