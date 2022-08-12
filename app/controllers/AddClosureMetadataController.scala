package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import controllers.util.CustomMetadataUtils
import controllers.util.CustomMetadataUtils.FieldValues
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
        customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
        customMetadataUtils = CustomMetadataUtils(customMetadata)
        propertyName = Set("ClosureType")
        value = "closed_for"

        dependencyProperties: Set[CustomMetadata] = getDependenciesFromValue(customMetadataUtils, propertyName, value: String)
          .filterNot(_.name == "DescriptionPublic")

        fieldsForForm: List[(FieldValues, String)] = customMetadataUtils.convertPropertiesToFields(dependencyProperties)
      } yield
        Ok(views.html.standard.addClosureMetadata(consignmentId, consignmentRef, fieldsForForm, request.token.name)).uncache()
  }

  private def getDependenciesFromValue(customMetadataUtils: CustomMetadataUtils,
                                       propertyName: Set[String],
                                       valueNameWithDependencies: String): Set[CustomMetadata] = {
    val valuesByProperties: Map[String, List[CustomMetadata.Values]] = customMetadataUtils.getValuesOfProperties(propertyName)
    val allValuesForProperty: Seq[CustomMetadata.Values] = valuesByProperties(propertyName.head)
    val valueWithDependencies: Option[CustomMetadata.Values] = allValuesForProperty.find(_.value == valueNameWithDependencies)
    val dependencyNames: Seq[String] = valueWithDependencies.map(_.dependencies.map(_.name)).getOrElse(Nil)
    customMetadataUtils.getCustomMetadataProperties(dependencyNames.toSet)
  }
}
