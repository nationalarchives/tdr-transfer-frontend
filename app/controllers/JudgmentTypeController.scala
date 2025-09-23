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
            JUDGMENT_TYPE -> "judgment",
            JUDGMENT_UPDATE -> "true",
            JUDGMENT_UPDATE_TYPE -> formData.updateType.value.mkString(";"),
            JUDGMENT_UPDATE_DETAILS -> formData.updateDetails.value.headOption.getOrElse("")
          )
        case _ => Map(JUDGMENT_TYPE -> formData.judgmentType.value.head, JUDGMENT_UPDATE -> "false", JUDGMENT_UPDATE_TYPE -> "", JUDGMENT_UPDATE_DETAILS -> "")
      }
      val defaultNcnMetadata = Map(NCN -> "", NO_NCN -> "", JUDGMENT_REFERENCE -> "")
      val validationErrors = ConsignmentProperty.validateFormData(metadata, List(BASE_SCHEMA, RELATIONSHIP_SCHEMA))
      if (validationErrors.exists(_._2.isEmpty)) {
        for {
          _ <- consignmentMetadataService.addOrUpdateConsignmentMetadata(consignmentId, metadata ++ defaultNcnMetadata, request.token.bearerAccessToken)
        } yield {
          if (metadata(JUDGMENT_TYPE) == "judgment" && metadata(JUDGMENT_UPDATE) == "false") {
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
      judgmentType = formData.judgmentType.copy(errors = errors.filter(_.property == JUDGMENT_TYPE).flatMap(ve => getErrorMessage(Some(ve)))),
      updateType = formData.updateType.copy(errors = errors.filter(_.property == JUDGMENT_UPDATE_TYPE).flatMap(ve => getErrorMessage(Some(ve)))),
      updateDetails = formData.updateDetails.copy(errors = errors.filter(_.property == JUDGMENT_UPDATE_DETAILS).flatMap(ve => getErrorMessage(Some(ve)))),
      errorSummary = errors.map(ve => ve.property -> Seq(ConsignmentProperty.getErrorMessage(Some(ve)).getOrElse("")))
    )
  }

  private def extractFormData(request: Request[AnyContent]) = {
    val formData = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val judgmentType = FormField(JUDGMENT_TYPE, formData(JUDGMENT_TYPE))
    val updateReason = FormField(JUDGMENT_UPDATE_TYPE, formData.getOrElse(JUDGMENT_UPDATE_TYPE, Seq.empty))
    val updateDetails = FormField(JUDGMENT_UPDATE_DETAILS, formData.getOrElse(JUDGMENT_UPDATE_DETAILS, Seq.empty))
    JudgmentTypeFormData(judgmentType, updateReason, updateDetails)
  }

  private def fillJudgmentTypeFormData(consignmentMetadata: GetConsignment) = {
    val metadata = consignmentMetadata.consignmentMetadata.map(md => md.propertyName -> md.value).toMap
    val existingUpdateType = metadata.get(tdrDataLoadHeaderMapper(JUDGMENT_UPDATE_TYPE)).filter(_.nonEmpty).map(_.split(";").toSeq).getOrElse(Seq.empty)
    val existingJudgmentType = if (existingUpdateType.nonEmpty) "update" else metadata.getOrElse(tdrDataLoadHeaderMapper(JUDGMENT_TYPE), "judgment")
    val existingUpdateDetails = metadata.getOrElse(tdrDataLoadHeaderMapper(JUDGMENT_UPDATE_DETAILS), "")
    JudgmentTypeFormData(
      FormField(JUDGMENT_TYPE, Seq(existingJudgmentType)),
      FormField(JUDGMENT_UPDATE_TYPE, existingUpdateType),
      FormField(JUDGMENT_UPDATE_DETAILS, Seq(existingUpdateDetails))
    )
  }
}

case class JudgmentTypeFormData(judgmentType: FormField, updateType: FormField, updateDetails: FormField, errorSummary: Seq[(String, Seq[String])] = Nil)
case class FormField(id: String, value: Seq[String], errors: Seq[String] = Nil)
