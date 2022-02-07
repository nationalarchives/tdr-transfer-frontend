package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.{I18nSupport, Lang, Langs, Messages}
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.{ConsignmentService, ConsignmentStatusService, TransferAgreementService}
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BeforeYouUploadController @Inject()(val controllerComponents: SecurityComponents,
                                            val graphqlConfiguration: GraphQLConfiguration,
                                            val keycloakConfiguration: KeycloakConfiguration,
                                            val consignmentService: ConsignmentService,
                                            langs: Langs)
                                           (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  def beforeYouUpload(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    Future(Ok(views.html.judgment.judgmentBeforeYouUpload(consignmentId, request.token.name)))
  }
}
