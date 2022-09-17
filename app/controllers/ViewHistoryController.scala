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

class ViewHistoryController @Inject()(val consignmentService: ConsignmentService,
                                      val keycloakConfiguration: KeycloakConfiguration,
                                      val controllerComponents: SecurityComponents
                                     ) extends TokenSecurity {
  def viewConsignments(): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.viewHistory(request.token.name))
  }
}
