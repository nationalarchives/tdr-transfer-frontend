package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.ConsignmentProperty
import controllers.util.ConsignmentProperty._
import graphql.codegen.GetConsignmentMetadata.getConsignmentMetadata.GetConsignment
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentMetadataService, ConsignmentService}
import uk.gov.nationalarchives.tdr.schema.generated.BaseSchema._
import uk.gov.nationalarchives.tdr.validation.schema.JsonSchemaDefinition.{BASE_SCHEMA, RELATIONSHIP_SCHEMA}
import uk.gov.nationalarchives.tdr.validation.schema.ValidationError

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class JudgmentTypeController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val consignmentMetadataService: ConsignmentMetadataService,
    val applicationConfig: ApplicationConfig
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  private val updateTypeFormIdAndLabels: Seq[(String, String)] = Seq(
    ("typo", "Typo"),
    ("Formatting", "Formatting"),
    ("NCN", "Amendment to NCN"),
    ("Anonymisation", "Anonymisation/redaction"),
    ("Other", "Other")
  )

  def selectJudgmentType(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignmentMetadata <- consignmentService.getConsignmentMetadata(consignmentId, request.token.bearerAccessToken)
    } yield {
      val formData = fillJudgmentTypeFormData(consignmentMetadata)
      Ok(views.html.judgment.judgmentDocumentType(consignmentId, consignmentMetadata.consignmentReference, request.token.name, updateTypeFormIdAndLabels, formData))
    }
  }

  def submitJudgmentType(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    {
      val formData = extractFormData(request)
      val metadata = formData.judgmentType.value.head match {
        case "update" =>
          Map(
            judgment_type -> "judgment",
            judgment_update -> "true",
            judgment_update_type -> formData.updateType.value.mkString(";"),
            judgment_update_details -> formData.updateDetails.value.headOption.getOrElse("")
          )
        case _ => Map(judgment_type -> formData.judgmentType.value.head, judgment_update -> "false", judgment_update_type -> "", judgment_update_details -> "")
      }
      val defaultNcnMetadata = Map(judgment_neutral_citation -> "", judgment_no_neutral_citation -> "", judgment_reference -> "")
      val validationErrors = ConsignmentProperty.validateFormData(metadata, List(BASE_SCHEMA, RELATIONSHIP_SCHEMA))
      if (validationErrors.exists(_._2.isEmpty)) {
        for {
          _ <- consignmentMetadataService.addOrUpdateConsignmentMetadata(consignmentId, metadata ++ defaultNcnMetadata, request.token.bearerAccessToken)
        } yield {
          if (metadata(judgment_type) == "judgment" && metadata(judgment_update) == "false") {
            Redirect(controllers.routes.BeforeUploadingController.beforeUploading(consignmentId))
          } else {
            Redirect(controllers.routes.JudgmentNeutralCitationController.addNCN(consignmentId))
          }
        }
      } else {
        val updatedFormData = updateFormDateWithErrors(formData, validationErrors)
        consignmentService
          .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
          .map(reference => BadRequest(views.html.judgment.judgmentDocumentType(consignmentId, reference, request.token.name, updateTypeFormIdAndLabels, updatedFormData)))
      }

    }
  }

  private def updateFormDateWithErrors(formData: JudgmentTypeFormData, validationErrors: Map[String, List[ValidationError]]): JudgmentTypeFormData = {
    val errors = validationErrors.values.flatten.toSeq
    JudgmentTypeFormData(
      judgmentType = formData.judgmentType.copy(errors = errors.filter(_.property == judgment_type).flatMap(ve => getErrorMessage(Some(ve)))),
      updateType = formData.updateType.copy(errors = errors.filter(_.property == judgment_update_type).flatMap(ve => getErrorMessage(Some(ve)))),
      updateDetails = formData.updateDetails.copy(errors = errors.filter(_.property == judgment_update_details).flatMap(ve => getErrorMessage(Some(ve)))),
      errorSummary = errors.map(ve => ve.property -> Seq(ConsignmentProperty.getErrorMessage(Some(ve)).getOrElse("")))
    )
  }

  private def extractFormData(request: Request[AnyContent]) = {
    val formData = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val judgmentType = FormField(judgment_type, formData(judgment_type))
    val updateReason = FormField(judgment_update_type, formData.getOrElse(judgment_update_type, Seq.empty))
    val updateDetails = FormField(judgment_update_details, formData.getOrElse(judgment_update_details, Seq.empty))
    JudgmentTypeFormData(judgmentType, updateReason, updateDetails)
  }

  private def fillJudgmentTypeFormData(consignmentMetadata: GetConsignment) = {
    val metadata = consignmentMetadata.consignmentMetadata.map(md => md.propertyName -> md.value).toMap
    val existingUpdateType = metadata.get(tdrDataLoadHeaderMapper(judgment_update_type)).filter(_.nonEmpty).map(_.split(";").toSeq).getOrElse(Seq.empty)
    val existingJudgmentType = if (existingUpdateType.nonEmpty) "update" else metadata.getOrElse(tdrDataLoadHeaderMapper(judgment_type), "judgment")
    val existingUpdateDetails = metadata.getOrElse(tdrDataLoadHeaderMapper(judgment_update_details), "")
    JudgmentTypeFormData(
      FormField(judgment_type, Seq(existingJudgmentType)),
      FormField(judgment_update_type, existingUpdateType),
      FormField(judgment_update_details, Seq(existingUpdateDetails))
    )
  }
}

case class JudgmentTypeFormData(judgmentType: FormField, updateType: FormField, updateDetails: FormField, errorSummary: Seq[(String, Seq[String])] = Nil)
case class FormField(id: String, value: Seq[String], errors: Seq[String] = Nil)
