package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.ConsignmentProperty._
import graphql.codegen.GetConsignmentMetadata.getConsignmentMetadata.GetConsignment
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.{I18nSupport, Lang, MessagesApi}
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentMetadataService, ConsignmentService}
import uk.gov.nationalarchives.tdr.schema.generated.BaseSchema._
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

  implicit val defaultLang: Lang = Lang.defaultLang

  def addNCN(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignment <- consignmentService.getConsignmentMetadata(consignmentId, request.token.bearerAccessToken)
      metadata = consignment.consignmentMetadata.map(md => md.propertyName -> md.value).toMap
      judgmentType = metadata.getOrElse(tdrDataLoadHeaderMapper(judgment_type), "")
      judgmentUpdate = metadata.getOrElse(tdrDataLoadHeaderMapper(judgment_update), "")
    } yield {
      if (judgmentType == press_summary || judgmentUpdate == "true") {
        val formData = fillNCNFormData(consignment)
        Ok(views.html.judgment.judgmentNeutralCitationNumber(consignmentId, consignment.consignmentReference, request.token.name, formData))
      } else {
        Redirect(routes.JudgmentTypeController.selectJudgmentType(consignmentId).url)
      }
    }
  }

  def validateNCN(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val formData = extractFormData(request)
    val metadata = formData.neutralCitation.value.headOption match {
      case Some(ncn) if ncn.nonEmpty => Map(judgment_neutral_citation -> ncn, judgment_no_neutral_citation -> "false", judgment_reference -> "")
      case _                         =>
        Map(
          judgment_neutral_citation -> "",
          judgment_no_neutral_citation -> formData.noNeutralCitation.value.contains("true").toString,
          judgment_reference -> formData.judgmentReference.value.head
        )
    }
    val validationErrors = validateFormData(metadata, List(BASE_SCHEMA, RELATIONSHIP_SCHEMA))
    if (validationErrors.exists(_._2.isEmpty)) {
      for {
        _ <- consignmentMetadataService.addOrUpdateConsignmentMetadata(consignmentId, metadata, request.token.bearerAccessToken)
      } yield {
        Redirect(routes.UploadController.judgmentUploadPage(consignmentId).url)
      }
    } else {
      val updatedFormData = updateFormDataWithErrors(formData, validationErrors)
      for {
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      } yield {
        BadRequest(views.html.judgment.judgmentNeutralCitationNumber(consignmentId, reference, request.token.name, updatedFormData))
      }
    }
  }

  private def updateFormDataWithErrors(formData: NCNFormData, validationErrors: Map[String, List[ValidationError]]): NCNFormData = {
    val errors = validationErrors.values.flatten.toSeq
    NCNFormData(
      neutralCitation = formData.neutralCitation.copy(errors = errors.filter(_.property == judgment_neutral_citation).flatMap(ve => getErrorMessage(Some(ve)))),
      noNeutralCitation = formData.noNeutralCitation.copy(errors = errors.filter(_.property == judgment_no_neutral_citation).flatMap(ve => getErrorMessage(Some(ve)))),
      judgmentReference = formData.judgmentReference.copy(errors = errors.filter(_.property == judgment_reference).flatMap(ve => getErrorMessage(Some(ve)))),
      errorSummary = errors.map(ve => {
        if (ve.errorKey == "maxLength" && ve.property == judgment_reference) {
          ve.property -> Seq(messages("SCHEMA_BASE.judgment_reference.maxLength"))
        } else {
          ve.property -> Seq(getErrorMessage(Some(ve)).getOrElse(""))
        }
      })
    )
  }

  private def extractFormData(request: Request[AnyContent]) = {
    val formData = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val neutralCitation = FormField(judgment_neutral_citation, formData.getOrElse(judgment_neutral_citation, Seq.empty))
    val noNeutralCitation = FormField(judgment_no_neutral_citation, formData.getOrElse(judgment_no_neutral_citation, Seq.empty))
    val reference = if (noNeutralCitation.value.contains("true")) {
      FormField(judgment_reference, formData.getOrElse(judgment_reference, Seq.empty))
    } else {
      FormField(judgment_reference, Seq(""))
    }
    NCNFormData(neutralCitation, noNeutralCitation, reference)
  }

  private def fillNCNFormData(consignmentMetadata: GetConsignment): NCNFormData = {
    val metadata = consignmentMetadata.consignmentMetadata.map(md => md.propertyName -> md.value).toMap
    val existingNCN = metadata.getOrElse(tdrDataLoadHeaderMapper(judgment_neutral_citation), "")
    val existingNoNCN = metadata.getOrElse(tdrDataLoadHeaderMapper(judgment_no_neutral_citation), "")
    val existingJudgmentReference = metadata.getOrElse(tdrDataLoadHeaderMapper(judgment_reference), "")
    NCNFormData(
      FormField(judgment_neutral_citation, Seq(existingNCN)),
      FormField(judgment_no_neutral_citation, Seq(existingNoNCN)),
      FormField(judgment_reference, Seq(existingJudgmentReference))
    )
  }
}
case class NCNFormData(neutralCitation: FormField, noNeutralCitation: FormField, judgmentReference: FormField, errorSummary: Seq[(String, Seq[String])] = Nil)
