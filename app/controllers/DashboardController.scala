package controllers
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ActorMaterializer, Materializer}
import io.circe.generic.auto._
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import graphql.codegen.query.getSeries
import io.circe.Json
import javax.inject.{Inject, Singleton}
import org.pac4j.core.profile.{CommonProfile, ProfileManager}
import org.pac4j.play.PlayWebContext
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import sangria.renderer.QueryRenderer

import scala.concurrent.{ExecutionContext, Future}


@Singleton
class DashboardController @Inject()(val controllerComponents: SecurityComponents)(implicit ec: ExecutionContext)
  extends Security[CommonProfile] with I18nSupport with FailFastCirceSupport  {

  implicit val actorSystem: ActorSystem = ActorSystem("graphql-server")
  implicit val materializer: Materializer = Materializer(actorSystem)
  def dashboard(): Action[AnyContent] = Secure("OidcClient") { implicit request: Request[AnyContent] =>
    Ok(views.html.dashboard())
  }
  def create(): Action[AnyContent] = Secure("OidcClient").async { implicit request: Request[AnyContent] =>
    val webContext = new PlayWebContext(request, playSessionStore)
    val profileManager = new ProfileManager[CommonProfile](webContext)
    val profile = profileManager.get(true)
    val token: BearerAccessToken = profile.get().getAttribute("access_token").asInstanceOf[BearerAccessToken]
    val host = "localhost"
    val port = 8081
    val uri: Uri = Uri(s"https://$host:$port/graphql")
    val queryJson = Json.fromString(QueryRenderer.render(getSeries.document, QueryRenderer.Compact))
    val fields =
      List("query" -> queryJson)
    val body = Json.obj(fields: _*).noSpaces
    val entity = HttpEntity(ContentTypes.`application/json`, body)

    val responseFuture: Future[HttpResponse] = Http().singleRequest(HttpRequest(uri = "http://localhost:8081/graphql",
      method=HttpMethods.POST, headers=List(Authorization(OAuth2BearerToken(token.getValue))), entity=entity))

    val data: Future[getSeries.Data] = responseFuture.flatMap(Unmarshal(_).to[getSeries.Data])

    data.map(t => {
      val id = t.getSeries.head.seriesid
      Ok(views.html.dashboard())
    })
    //    responseFuture
    //      .map {
    //        case response @ HttpResponse(StatusCodes.OK, _, _, _) =>
    //          response.entity
    //        case _   => print(_)
    //      }
    //    Future.successful(Ok(views.html.dashboard()))

  }
}
