package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.util.CustomMetadataUtils.FieldValues
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import controllers.util.{CustomMetadataUtils, DynamicFormUtils}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService}
import play.api.cache._

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
      for {
        consignmentRef <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        orderedFieldsForForm <- {
          cache.set(s"$consignmentId", consignmentRef, 1.hour)
          getDefaultFieldsForForm(consignmentId, request)
        }
      } yield Ok(views.html.standard.addClosureMetadata(consignmentId, consignmentRef, orderedFieldsForForm, request.token.name))
  }

  def addClosureMetadataSubmit(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      for {
        defaultFieldValues <- cache.getOrElseUpdate[List[(FieldValues, String)]]("fieldValues") {
            getDefaultFieldsForForm(consignmentId, request)
        }
        dynamicFormUtils = new DynamicFormUtils(request, defaultFieldValues)
        formAnswers: Map[String, Seq[String]] = dynamicFormUtils.formAnswersWithValidInputNames
        validatedFormAnswers: Map[String, (Option[Any], List[String])] = dynamicFormUtils.validateFormAnswers(formAnswers)
        formAnswersContainAnError: Boolean = dynamicFormUtils.formAnswersContainAnError(validatedFormAnswers)

        result <- {
          if(formAnswersContainAnError) {
            val updatedFormFields: List[(FieldValues, String)] = dynamicFormUtils.convertSubmittedValuesToDefaultFieldValues(validatedFormAnswers)
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

  private def getDefaultFieldsForForm(consignmentId: UUID, request: Request[AnyContent]): Future[List[(FieldValues, String)]] = {
    for {
      customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
      customMetadataUtils = new CustomMetadataUtils(customMetadata)
      propertyName = Set("ClosureType")
      value = "closed_for"

      dependencyProperties: Set[CustomMetadata] = getDependenciesFromValue(customMetadataUtils, propertyName, value: String)
        .filterNot(_.name == "DescriptionPublic")

      fieldsForForm: List[(FieldValues, String)] = customMetadataUtils.convertPropertiesToFields(dependencyProperties)
    } yield {
      cache.set("fieldValues", fieldsForForm, 1.hour)
      fieldsForForm
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
}
