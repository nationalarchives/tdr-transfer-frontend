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

import java.net.URLEncoder
import java.util.{Properties, UUID}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import play.api.data._
import play.api.data.Forms._

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
  private val neutralCitationForm: Form[NeutralCitationData] = Form(
    mapping(
      NCN -> optional(text),
      NO_NCN -> optional(text)
        .transform[Boolean](_.contains("no-ncn-select"), b => if (b) Some("no-ncn-select") else None),
      JUDGMENT_REFERENCE -> optional(text)
    ) { (ncnOpt, noNcn, refOpt) =>
      NeutralCitationData(ncnOpt.map(_.trim).filter(_.nonEmpty), noNcn, refOpt.map(_.trim).filter(_.nonEmpty))
    } { data =>
      Some((data.neutralCitation, data.noNeutralCitation, data.judgmentReference))
    }
  )

  def addNCN(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val ncnValue: Option[String] = request.getQueryString(NCN).filter(_.nonEmpty)
    val rawNoNcnSelected: Boolean = request.getQueryString(NO_NCN).exists(_.contains("no-ncn-select"))
    val effectiveNoNcnSelected = rawNoNcnSelected && ncnValue.isEmpty
    val judgmentReference: Option[String] = if (effectiveNoNcnSelected) request.getQueryString(JUDGMENT_REFERENCE).filter(_.nonEmpty) else None
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map(reference => Ok(views.html.judgment.judgmentNeutralCitationNumber(consignmentId, reference, request.token.name, ncnValue, effectiveNoNcnSelected, judgmentReference)))
  }

  def validateNCN(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    neutralCitationForm
      .bindFromRequest()
      .fold(
        _ => handleValidation(consignmentId, NeutralCitationData(None, noNeutralCitation = false, None)),
        data => handleValidation(consignmentId, data)
      )
  }

  private def handleValidation(consignmentId: UUID, data: NeutralCitationData)(implicit request: Request[AnyContent]) = {
    val objectMetadata = neutralCitationToObjectMetadata(data)
    val ncnValue: Option[String] = data.neutralCitation
    val rawNoNcnSelected: Boolean = data.noNeutralCitation
    val effectiveNoNcnSelected: Boolean = rawNoNcnSelected && ncnValue.forall(_.isEmpty)
    val judgmentReference: Option[String] = if (effectiveNoNcnSelected) data.judgmentReference else None

    val validated = doValidation(objectMetadata)

    if (validated.isDefined) {
      consignmentService
        .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        .map(reference =>
          BadRequest(
            views.html.judgment.judgmentNeutralCitationNumberError(consignmentId, reference, request.token.name, validated.get, ncnValue, effectiveNoNcnSelected, judgmentReference)
          )
        )
    } else {
      val params = Seq(
        ncnValue.filter(_.nonEmpty).map(v => NCN -> v),
        if (effectiveNoNcnSelected) Some(NO_NCN -> "no-ncn-select") else None,
        if (effectiveNoNcnSelected) judgmentReference.filter(_.nonEmpty).map(v => JUDGMENT_REFERENCE -> v) else None
      ).flatten
      val base = routes.UploadController.judgmentUploadPage(consignmentId).url
      val url = if (params.nonEmpty) {
        val qs = params.map { case (k, v) => s"$k=${URLEncoder.encode(v, "UTF-8")}" }.mkString("&")
        s"$base?$qs"
      } else base
      Future.successful(Redirect(url))
    }
  }

  private def neutralCitationToObjectMetadata(data: NeutralCitationData): ObjectMetadata = {
    ObjectMetadata(
      "data",
      Set(
        Metadata(NCN, data.neutralCitation.getOrElse("")),
        Metadata(NO_NCN, if (data.noNeutralCitation) "yes" else "no"),
        Metadata(JUDGMENT_REFERENCE, data.judgmentReference.getOrElse(""))
      )
    )
  }

  private def doValidation(data: ObjectMetadata): Option[String] = {
    validateNCNValue(data).orElse(validateRelationships(data))
  }

  private def validateRelationships(data: ObjectMetadata): Option[String] = {
    val validationErrors: Map[String, List[ValidationError]] = MetadataValidationJsonSchema.validateWithSingleSchema(RELATIONSHIP_SCHEMA, Set(data))
    val errorOption: Option[ValidationError] = validationErrors.headOption.flatMap(x => x._2.headOption)
    validationErrorMessage(errorOption)
  }

  private def validationErrorMessage(errorOption: Option[ValidationError]) = {
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
          validationErrorMessage(errorOption)
        }
    }
  }

  private def getMessageProperties: Properties = {
    val source = Source.fromURL(getClass.getResource("/validation-messages/validation-messages.properties"))
    val properties = new Properties()
    properties.load(source.bufferedReader())
    properties
  }

  private case class NeutralCitationData(neutralCitation: Option[String], noNeutralCitation: Boolean, judgmentReference: Option[String])
}
