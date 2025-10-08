package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.ConsignmentProperty.{judgment, tdrDataLoadHeaderMapper}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService
import uk.gov.nationalarchives.tdr.schema.generated.BaseSchema.{judgment_type, judgment_update}

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class BeforeUploadingController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val applicationConfig: ApplicationConfig
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def beforeUploading(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val backURL = if (applicationConfig.blockJudgmentPressSummaries) {
      routes.HomepageController.homepage().url
    } else {
      routes.JudgmentTypeController.selectJudgmentType(consignmentId).url
    }
    for {
      consignment <- consignmentService.getConsignmentMetadata(consignmentId, request.token.bearerAccessToken)
    } yield {
      val metadata = consignment.consignmentMetadata.map(md => md.propertyName -> md.value).toMap
      val judgmentType = metadata.getOrElse(tdrDataLoadHeaderMapper(judgment_type), "")
      val judgmentUpdate = metadata.getOrElse(tdrDataLoadHeaderMapper(judgment_update), "false").toBoolean
      if (applicationConfig.blockJudgmentPressSummaries || (judgmentType == judgment && !judgmentUpdate)) {
        Ok(views.html.judgment.judgmentBeforeUploading(consignmentId, consignment.consignmentReference, request.token.name, backURL))
      } else {
        Redirect(routes.JudgmentTypeController.selectJudgmentType(consignmentId).url)
      }
    }
  }
}
