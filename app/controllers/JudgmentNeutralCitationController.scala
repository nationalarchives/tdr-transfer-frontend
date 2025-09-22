package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.ConsignmentProperty._
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.{I18nSupport, Lang, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentMetadataService, ConsignmentService}
import uk.gov.nationalarchives.tdr.validation.schema.JsonSchemaDefinition.{BASE_SCHEMA, RELATIONSHIP_SCHEMA}
import uk.gov.nationalarchives.tdr.validation.schema.ValidationError

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class JudgmentNeutralCitationController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val consignmentMetadataService: ConsignmentMetadataService,
    val messages: MessagesApi
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def addNCN(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      neutralCitationData <- consignmentMetadataService.getNeutralCitationData(consignmentId, request.token.bearerAccessToken)
      consignmentRef <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield Ok(
      views.html.judgment
        .judgmentNeutralCitationNumber(
          consignmentId,
          consignmentRef,
          request.token.name,
          neutralCitationData.neutralCitation,
          neutralCitationData.noNeutralCitation,
          neutralCitationData.judgmentReference
        )
    )
  }

  def validateNCN(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val data: NeutralCitationData = extractFormData(request)
    handleValidation(consignmentId, data)
  }

  private def handleValidation(consignmentId: UUID, ncnData: NeutralCitationData)(implicit request: Request[AnyContent]) = {

    // Only send judgmentReference if noNeutralCitation is true
    val judgmentReference: Option[String] = if (ncnData.noNeutralCitation) ncnData.judgmentReference else None

    val validatedNCN = validateNCNDataValue(ncnData.neutralCitation, NCN).orElse(validateRelationships(ncnData))

    if (validatedNCN.isDefined) {
      consignmentService
        .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        .map(reference =>
          BadRequest(
            views.html.judgment.judgmentNeutralCitationNumberError(
              consignmentId,
              reference,
              request.token.name,
              validatedNCN.get,
              ncnData.neutralCitation,
              ncnData.noNeutralCitation,
              judgmentReference
            )
          )
        )
    } else {
      val validatedNoNCNReference = validateNCNDataValue(ncnData.judgmentReference, JUDGMENT_REFERENCE)
      implicit val defaultLang: Lang = Lang.defaultLang
      if (validatedNoNCNReference.isDefined) {
        consignmentService
          .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
          .map(reference =>
            BadRequest(
              views.html.judgment.judgmentNoNeutralCitationNumberReferenceError(
                consignmentId,
                reference,
                request.token.name,
                messages("SCHEMA_BASE.judgment_reference.maxLength"), // get this problem message from messages as not a validation error
                ncnData.neutralCitation,
                ncnData.noNeutralCitation,
                judgmentReference,
                validatedNoNCNReference
              )
            )
          )
      } else {
        val url: String = routes.UploadController.judgmentUploadPage(consignmentId).url
        consignmentMetadataService
          .addOrUpdateConsignmentNeutralCitationNumber(consignmentId, ncnData, request.token.bearerAccessToken)
          .map(_ => Redirect(url))
      }
    }
  }

  private def validateRelationships(data: NeutralCitationData): Option[String] = {
    val validationErrors: Map[String, List[ValidationError]] = validateNeutralCitationData(data, RELATIONSHIP_SCHEMA)
    val errorOption: Option[ValidationError] = validationErrors.headOption.flatMap(x => x._2.headOption)
    validationErrorMessage(errorOption)
  }

  private def validateNCNDataValue(value: Option[String], valueKey: String): Option[String] = {
    value match {
      case None | Some("") => None
      case Some(v) =>
        val wrapped: Option[String] = Some(v)
        val neutralCitationData = valueKey match {
          case JUDGMENT_REFERENCE => NeutralCitationData(judgmentReference = wrapped)
          case NCN                => NeutralCitationData(neutralCitation = wrapped)
        }
        val validationErrors: Map[String, List[ValidationError]] = validateNeutralCitationData(neutralCitationData, BASE_SCHEMA)
        val errorOption: Option[ValidationError] = {
          validationErrors.find(p => p._2.exists(error => error.property == valueKey)).flatMap(_._2.headOption)
        }
        validationErrorMessage(errorOption)
    }
  }

  private def extractFormData(request: Request[AnyContent]) = {
    val formData = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    NeutralCitationData(
      formData.get(NCN).flatMap(_.headOption),
      formData.get(NO_NCN).exists(_.contains("no-ncn-select")),
      formData.get(JUDGMENT_REFERENCE).flatMap(_.headOption)
    )
  }
}
