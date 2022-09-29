package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.MetadataProperty._
import controllers.util._
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.Metadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import org.pac4j.play.scala.SecurityComponents
import play.api.cache._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService}

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

class AddClosureMetadataController @Inject()(val controllerComponents: SecurityComponents,
                                             val graphqlConfiguration: GraphQLConfiguration,
                                             val keycloakConfiguration: KeycloakConfiguration,
                                             val consignmentService: ConsignmentService,
                                             val customMetadataService: CustomMetadataService,
                                             val cache: AsyncCacheApi)
                                            (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  def addClosureMetadata(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      //  TODO:  Get selectedFileIds from previous page
    val fileFilters = None
      for {
        consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, fileFilters)
        defaultFieldForm <- getDefaultFieldsForForm(consignmentId, request)
        updatedFieldsForForm <- {
          cache.set(s"$consignmentId", consignment.consignmentReference, 1.hour)
          updateFormFields(defaultFieldForm, consignment.files.headOption.map(_.metadata))
        }
      } yield Ok(views.html.standard.addClosureMetadata(consignmentId, consignment.consignmentReference, updatedFieldsForForm, request.token.name))
  }

  def addClosureMetadataSubmit(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      for {
        defaultFieldValues <- cache.getOrElseUpdate[List[FormField]]("fieldValues") {
          getDefaultFieldsForForm(consignmentId, request)
        }
        dynamicFormUtils = new DynamicFormUtils(request, defaultFieldValues)
        formAnswers: Map[String, Seq[String]] = dynamicFormUtils.formAnswersWithValidInputNames

        result <- {
          val updatedFormFields: List[FormField] = dynamicFormUtils.validateAndConvertSubmittedValuesToFormFields(formAnswers)
          if (updatedFormFields.exists(_.fieldErrors.nonEmpty)) {
            for {
              consignmentRef <- cache.getOrElseUpdate[String](s"$consignmentId") {
                consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
              }
            } yield Ok(views.html.standard.addClosureMetadata(consignmentId, consignmentRef, updatedFormFields, request.token.name))
          } else {
            // A call to the API to save data to database should go here.
            Future(Ok(views.html.standard.homepage(request.token.name))) // this view should be replaced with closure metadata overview page
          }
        }
      } yield result
  }

  private def getDefaultFieldsForForm(consignmentId: UUID, request: Request[AnyContent]): Future[List[FormField]] = {
    for {
      customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
      customMetadataUtils = new CustomMetadataUtils(customMetadata)
      propertyName = Set("ClosureType")
      value = "Closed"

      dependencyProperties: Set[CustomMetadata] = getDependenciesFromValue(customMetadataUtils, propertyName, value)
        .filterNot(_.name == "DescriptionPublic")

      formFields = customMetadataUtils.convertPropertiesToFormFields(dependencyProperties)
    } yield {
      cache.set("fieldValues", formFields, 1.hour)
      formFields
    }
  }

  private def getDependenciesFromValue(customMetadataUtils: CustomMetadataUtils,
                                       propertyName: Set[String],
                                       valueToGetDependenciesFrom: String): Set[CustomMetadata] = {
    val valuesByProperties: Map[String, List[CustomMetadata.Values]] = customMetadataUtils.getValuesOfProperties(propertyName)
    val allValuesForProperty: Seq[CustomMetadata.Values] = valuesByProperties(propertyName.head)
    val value: Seq[CustomMetadata.Values] = allValuesForProperty.filter(_.value == valueToGetDependenciesFrom)
    val dependencyNames: Seq[String] = value.flatMap(_.dependencies.map(_.name))
    customMetadataUtils.getCustomMetadataProperties(dependencyNames.toSet)
  }

  private def updateFormFields(orderedFieldsForForm: List[FormField], fileMetadata: Option[Metadata]): Future[List[FormField]] = {

    Future(
      fileMetadata.map(fileMetadata =>
        orderedFieldsForForm.map(field =>
          (field.fieldId match {
            case `foiExemptionAsserted` => fileMetadata.foiExemptionAsserted.map(p => DateField.update(field.asInstanceOf[DateField], p))
            case `closureStartDate` => fileMetadata.closureStartDate.map(p => DateField.update(field.asInstanceOf[DateField], p))
            case `closurePeriod` => fileMetadata.closurePeriod.map(p => TextField.update(field.asInstanceOf[TextField], p.toString))
            case `foiExemptionCode` => fileMetadata.foiExemptionCode.map(DropdownField.update(field.asInstanceOf[DropdownField], _))
            case `titlePublic` => fileMetadata.titlePublic.map(RadioButtonGroupField.update(field.asInstanceOf[RadioButtonGroupField], _))
            case _ => None
          }).getOrElse(field))
      ).getOrElse(orderedFieldsForForm)
    )
  }
}
