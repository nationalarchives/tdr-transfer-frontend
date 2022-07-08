package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetClosureMetadata.closureMetadata.ClosureMetadata
import controllers.util.ClosureMetadataUtils
import controllers.util.FieldValues
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService}
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.Inject
import scala.collection.immutable.ListSet
import scala.concurrent.ExecutionContext

class AddClosureMetadataController @Inject()(val controllerComponents: SecurityComponents,
                                             val graphqlConfiguration: GraphQLConfiguration,
                                             val keycloakConfiguration: KeycloakConfiguration,
                                             val consignmentService: ConsignmentService,
                                             val customMetadataService: CustomMetadataService)
                                            (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  def addClosureMetadata(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      for {
        consignmentRef <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
        closureMetadata <- customMetadataService.getClosureMetadata(consignmentId, request.token.bearerAccessToken)
        closureMetadataUtils = new ClosureMetadataUtils(closureMetadata)
        propertyName = Set("ClosureType")
        value = "closed_for"

        dependencyProperties: Set[ClosureMetadata] = getDependenciesFromValue(closureMetadataUtils, propertyName, value: String)
          .filterNot(_.name == "DescriptionPublic")

        fieldsForForm: Set[(FieldValues, String)] = closureMetadataUtils.convertPropertiesToFields(dependencyProperties)
        orderedFieldsForForm = closureMetadataUtils.sortMetadataIntoCorrectPageOrder(fieldsForForm)
      } yield Ok(views.html.standard.addClosureMetadata(consignmentId, consignmentRef, orderedFieldsForForm, request.token.name)).uncache()
  }

  private def getDependenciesFromValue(customMetadataUtils: ClosureMetadataUtils,
                                       propertyName: Set[String],
                                       valueToGetDependenciesFrom: String): Set[ClosureMetadata] = {
    val valuesByProperties: Map[String, List[ClosureMetadata.Values]] = customMetadataUtils.getValuesOfProperties(propertyName)
    val allValuesForProperty: Seq[ClosureMetadata.Values] = valuesByProperties(propertyName.head)
    val value: ClosureMetadata.Values = allValuesForProperty.find(_.value == valueToGetDependenciesFrom).get
    val dependencyNames: Seq[String] = value.dependencies.map(_.name)
    // Dependencies are slightly shorter versions of the properties (missing the "values" field)
    customMetadataUtils.getClosureMetadataProperties(dependencyNames.toSet)
  }
}
