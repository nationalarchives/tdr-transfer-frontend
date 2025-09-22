package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.ConsignmentProperty._
import graphql.codegen.GetConsignmentMetadata.getConsignmentMetadata.GetConsignment
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

  implicit val defaultLang: Lang = Lang.defaultLang

  def addNCN(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    for {
      consignmentMetadata <- consignmentService.getConsignmentMetadata(consignmentId, request.token.bearerAccessToken)
    } yield {
      val formData = fillNCNFormData(consignmentMetadata)
      Ok(views.html.judgment.judgmentNeutralCitationNumber(consignmentId, consignmentMetadata.consignmentReference, request.token.name, formData))
    }
  }

  def validateNCN(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val formData = extractFormData(request)
    val metadata = formData.neutralCitation.value.headOption match {
      case Some(ncn) if ncn.nonEmpty => Map(NCN -> ncn, NO_NCN -> "false", JUDGMENT_REFERENCE -> "")
      case _ =>
        Map(
          NCN -> "",
          NO_NCN -> formData.noNeutralCitation.value.contains("true").toString,
          JUDGMENT_REFERENCE -> formData.judgmentReference.value.head
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
      val updatedFormData = updateFormDateWithErrors(formData, validationErrors)
      for {
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      } yield {
        BadRequest(views.html.judgment.judgmentNeutralCitationNumber(consignmentId, reference, request.token.name, updatedFormData))
      }
    }
  }

  private def updateFormDateWithErrors(formData: NCNFormData, validationErrors: Map[String, List[ValidationError]]): NCNFormData = {
    val errors = validationErrors.values.flatten.toSeq
    NCNFormData(
      neutralCitation = formData.neutralCitation.copy(errors = errors.filter(_.property == NCN).flatMap(ve => getErrorMessage(Some(ve)))),
      noNeutralCitation = formData.noNeutralCitation.copy(errors = errors.filter(_.property == NO_NCN).flatMap(ve => getErrorMessage(Some(ve)))),
      judgmentReference = formData.judgmentReference.copy(errors = errors.filter(_.property == JUDGMENT_REFERENCE).flatMap(ve => getErrorMessage(Some(ve)))),
      errorSummary = errors.map(ve => {
        if (ve.errorKey == "maxLength" && ve.property == JUDGMENT_REFERENCE) {
          ve.property -> Seq(messages("SCHEMA_BASE.judgment_reference.maxLength"))
        } else {
          ve.property -> Seq(getErrorMessage(Some(ve)).getOrElse(""))
        }
      })
    )
  }

  private def extractFormData(request: Request[AnyContent]) = {
    val formData = request.body.asFormUrlEncoded.getOrElse(Map.empty)
    val neutralCitation = FormField(NCN, formData.getOrElse(NCN, Seq.empty))
    val noNeutralCitation = FormField(NO_NCN, formData.getOrElse(NO_NCN, Seq.empty))
    val reference = if (noNeutralCitation.value.contains("true")) {
      FormField(JUDGMENT_REFERENCE, formData.getOrElse(JUDGMENT_REFERENCE, Seq.empty))
    } else {
      FormField(JUDGMENT_REFERENCE, Seq(""))
    }
    NCNFormData(neutralCitation, noNeutralCitation, reference)
  }

  private def fillNCNFormData(consignmentMetadata: GetConsignment): NCNFormData = {
    val metadata = consignmentMetadata.consignmentMetadata.map(md => md.propertyName -> md.value).toMap
    val existingNCN = metadata.getOrElse(tdrDataLoadHeaderMapper(NCN), "")
    val existingNoNCN = metadata.getOrElse(tdrDataLoadHeaderMapper(NO_NCN), "")
    val existingJudgmentReference = metadata.getOrElse(tdrDataLoadHeaderMapper(JUDGMENT_REFERENCE), "")
    NCNFormData(
      FormField(NCN, Seq(existingNCN)),
      FormField(NO_NCN, Seq(existingNoNCN)),
      FormField(JUDGMENT_REFERENCE, Seq(existingJudgmentReference))
    )
  }
}
case class NCNFormData(neutralCitation: FormField, noNeutralCitation: FormField, judgmentReference: FormField, errorSummary: Seq[(String, Seq[String])] = Nil)
