package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService
import uk.gov.nationalarchives.tdr.validation.Metadata
import uk.gov.nationalarchives.tdr.validation.schema.JsonSchemaDefinition.{BASE_SCHEMA, RELATIONSHIP_SCHEMA}
import uk.gov.nationalarchives.tdr.validation.schema.MetadataValidationJsonSchema.ObjectMetadata
import uk.gov.nationalarchives.tdr.validation.schema.{MetadataValidationJsonSchema, ValidationError}

import java.util.{Properties, UUID}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source

@Singleton
class JudgmentNeutralCitationController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  private lazy val properties = getMessageProperties
  private val NCN = "judgment_neutral_citation"
  private val NO_NCN = "judgment_no_neutral_citation"
  private val JUDGMENT_REFERENCE = "judgment_reference"

  def page(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map(reference => Ok(views.html.judgment.judgmentNeutralCitationNumber(consignmentId, reference, request.token.name)))
  }

  def validateNCN(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val data: ObjectMetadata = extractFormData(request)
    val validated = doValidation(data)
    if (validated.isDefined) {
      consignmentService
        .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        .map(reference => BadRequest(views.html.judgment.judgmentNeutralCitationNumberError(consignmentId, reference, request.token.name, validated.get)))
    } else {
      Future.successful(Redirect(routes.UploadController.judgmentUploadPage(consignmentId)))
    }
  }

  private def extractFormData(request: Request[AnyContent]) = {
    val formData = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val neutralCitation = formData.get(NCN).flatMap(_.headOption).map(_.trim).filter(_.nonEmpty).getOrElse("")
    val noNcnSelected = if (formData.get(NO_NCN).exists(_.contains("no-ncn-select"))) "yes" else "no"
    val judgmentReference = formData.get("judgment-reference").flatMap(_.headOption).getOrElse("")

    ObjectMetadata(
      "data",
      Set(
        Metadata(NCN, neutralCitation),
        Metadata(NO_NCN, noNcnSelected),
        Metadata(JUDGMENT_REFERENCE, judgmentReference)
      )
    )
  }

  private def doValidation(data: ObjectMetadata): Option[String] = {
    validateNCNValue(data).orElse(validateRelationships(data))
  }

  private def validateRelationships(data: ObjectMetadata): Option[String] = {
    val validationErrors: Map[String, List[ValidationError]] = MetadataValidationJsonSchema.validateWithSingleSchema(RELATIONSHIP_SCHEMA, Set(data))
    val errorOption: Option[ValidationError] = validationErrors.headOption.flatMap(x => x._2.headOption)
    errorOption.map(ve => {
      properties.getProperty(s"${ve.validationProcess}.${ve.property}.${ve.errorKey}", s"${ve.validationProcess}.${ve.property}.${ve.errorKey}")
    })
  }

  private def validateNCNValue(data: ObjectMetadata): Option[String] = {
    data.metadata.find(_.name == NCN) match {
      case None => None
      case Some(ncnMetadata) =>
        val ncnValue = ncnMetadata.value
        if (ncnValue.isEmpty) {
          None
        } else {
          val validationErrors: Map[String, List[ValidationError]] = MetadataValidationJsonSchema.validateWithSingleSchema(BASE_SCHEMA, Set(data))
          val errorOption: Option[ValidationError] = {
            validationErrors.find(p => p._2.exists(error => error.property == NCN)).flatMap(_._2.headOption)
          }
          errorOption.map(ve => {
            properties.getProperty(s"${ve.validationProcess}.${ve.property}.${ve.errorKey}", s"${ve.validationProcess}.${ve.property}.${ve.errorKey}")
          })
        }
    }
  }

  private def getMessageProperties: Properties = {
    val source = Source.fromURL(getClass.getResource("/validation-messages/validation-messages.properties"))
    val properties = new Properties()
    properties.load(source.bufferedReader())
    properties
  }
}
