package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.ConsignmentProperty._
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentMetadataService, ConsignmentService}
import uk.gov.nationalarchives.tdr.validation.schema.JsonSchemaDefinition.{BASE_SCHEMA, RELATIONSHIP_SCHEMA}
import uk.gov.nationalarchives.tdr.validation.schema.ValidationError

import java.net.URLEncoder
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class JudgmentNeutralCitationController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val consignmentMetadataService: ConsignmentMetadataService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def addNCN(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val ncnValue: Option[String] = request.getQueryString(NCN).filter(_.nonEmpty)
    val noNcnSelected: Boolean = request.getQueryString(NO_NCN).exists(_.contains("no-ncn-select"))
    val effectiveNoNcnSelected = noNcnSelected && ncnValue.isEmpty
    // UI does not allow both NCN and no NCN to be selected, so only send judgmentReference if noNCN is selected
    val judgmentReference: Option[String] = if (effectiveNoNcnSelected) request.getQueryString(JUDGMENT_REFERENCE).filter(_.nonEmpty) else None
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map(reference => Ok(views.html.judgment.judgmentNeutralCitationNumber(consignmentId, reference, request.token.name, ncnValue, effectiveNoNcnSelected, judgmentReference)))
  }

  def validateNCN(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val data: NeutralCitationData = extractFormData(request)
    handleValidation(consignmentId, data)
  }

  private def handleValidation(consignmentId: UUID, ncnData: NeutralCitationData)(implicit request: Request[AnyContent]) = {

    // Only send judgmentReference if noNeutralCitation is true
    val judgmentReference: Option[String] = if (ncnData.noNeutralCitation) ncnData.judgmentReference else None

    val validated = doValidation(ncnData)

    if (validated.isDefined) {
      consignmentService
        .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        .map(reference =>
          BadRequest(
            views.html.judgment.judgmentNeutralCitationNumberError(
              consignmentId,
              reference,
              request.token.name,
              validated.get,
              ncnData.judgmentReference,
              ncnData.noNeutralCitation,
              judgmentReference
            )
          )
        )
    } else {
      val url: String = buildBackURL(consignmentId, ncnData)

      consignmentMetadataService
        .addOrUpdateConsignmentNeutralCitationNumber(consignmentId, ncnData, request.token.bearerAccessToken)
        .map(_ => Redirect(url))
    }
  }

  private def buildBackURL(consignmentId: UUID, ncnData: NeutralCitationData) = {
    val params: Seq[(String, String)] = Seq(
      if (ncnData.neutralCitation.nonEmpty) Some(NCN -> ncnData.neutralCitation.get) else None,
      if (ncnData.noNeutralCitation) Some(NO_NCN -> "no-ncn-select") else None,
      if (ncnData.noNeutralCitation) ncnData.judgmentReference.map(reference => JUDGMENT_REFERENCE -> reference) else None
    ).flatten
    val base = routes.UploadController.judgmentUploadPage(consignmentId).url
    val url = if (params.nonEmpty) {
      val encodedParameters = params.map { case (k, v) => s"$k=${URLEncoder.encode(v, "UTF-8")}" }.mkString("&")
      s"$base?$encodedParameters"
    } else base
    url
  }

  private def doValidation(data: NeutralCitationData): Option[String] = {
    validateNCNValue(data).orElse(validateRelationships(data))
  }

  private def validateRelationships(data: NeutralCitationData): Option[String] = {
    val validationErrors: Map[String, List[ValidationError]] = validateNeutralCitationData(data, RELATIONSHIP_SCHEMA)
    val errorOption: Option[ValidationError] = validationErrors.headOption.flatMap(x => x._2.headOption)
    validationErrorMessage(errorOption)
  }

  private def validateNCNValue(data: NeutralCitationData): Option[String] = {
    data.neutralCitation match {
      case None => None
      case Some(ncnMetadata) =>
        if (ncnMetadata.isEmpty) {
          None
        } else {
          val validationErrors: Map[String, List[ValidationError]] = validateNeutralCitationData(data, BASE_SCHEMA)

          val errorOption: Option[ValidationError] = {
            validationErrors.find(p => p._2.exists(error => error.property == NCN)).flatMap(_._2.headOption)
          }
          validationErrorMessage(errorOption)
        }
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
