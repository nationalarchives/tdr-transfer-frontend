package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.AddAdditionalMetadataController.{File, formFieldOverrides}
import controllers.util.MetadataProperty.{clientSideOriginalFilepath, closureType, description, descriptionClosed}
import controllers.util._
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.{FileFilters, UpdateFileMetadataInput}
import org.pac4j.play.scala.SecurityComponents
import play.api.cache._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService, DisplayPropertiesService}

import java.sql.Timestamp
import java.time.{LocalDate, LocalDateTime, LocalTime}
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class AddAdditionalMetadataController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val customMetadataService: CustomMetadataService,
    val displayPropertiesService: DisplayPropertiesService,
    val cache: AsyncCacheApi
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  def addAdditionalMetadata(consignmentId: UUID, metadataType: String, fileIds: List[UUID]): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      for {
        consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, Option(FileFilters(None, Option(fileIds), None, None)))
        formFields <- getFormFields(consignmentId, request, metadataType)
        updatedFormFields <- {
          cache.set(s"$consignmentId-consignment", consignment, 1.hour)
          // Set the values to those of the first file's metadata until we decide what to do with multiple files.
          val metadataMap = consignment.files.headOption.map(_.fileMetadata).getOrElse(Nil).groupBy(_.name).view.mapValues(_.head).toMap
          Future.successful(updateFormFields(formFields, metadataMap))
        }
      } yield {
        Ok(
          views.html.standard
            .addAdditionalMetadata(consignmentId, consignment.consignmentReference, metadataType, updatedFormFields, request.token.name, consignment.files.toFiles)
        )
      }
  }

  def addAdditionalMetadataSubmit(consignmentId: UUID, metadataType: String, fileIds: List[UUID]): Action[AnyContent] = standardTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      for {
        formFields <- cache.getOrElseUpdate[List[FormField]]("formFields") {
          getFormFields(consignmentId, request, metadataType)
        }
        dynamicFormUtils = new DynamicFormUtils(request, formFields)
        formAnswers: Map[String, Seq[String]] = dynamicFormUtils.formAnswersWithValidInputNames

        result <- {
          val updatedFormFields: List[FormField] = dynamicFormUtils.convertSubmittedValuesToFormFields(formAnswers)
          if (updatedFormFields.exists(_.fieldErrors.nonEmpty)) {
            for {
              consignment <- cache.getOrElseUpdate[GetConsignment](s"$consignmentId-consignment") {
                consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, Option(FileFilters(None, Option(fileIds), None, None)))
              }
              metadataMap = consignment.files.headOption.map(_.fileMetadata).getOrElse(Nil).groupBy(_.name).view.mapValues(_.head).toMap
            } yield {
              val files = consignment.files.toFiles
              Ok(
                views.html.standard
                  .addAdditionalMetadata(
                    consignmentId,
                    consignment.consignmentReference,
                    metadataType,
                    updatedFormFields.map(formFieldOverrides(_, metadataMap)),
                    request.token.name,
                    files
                  )
              )
            }
          } else {
            deleteDependencyProperties(updatedFormFields, fileIds)
            val metadataInput: List[UpdateFileMetadataInput] = buildUpdateMetadataInput(updatedFormFields)
            customMetadataService
              .saveMetadata(consignmentId, fileIds, request.token.bearerAccessToken, metadataInput)
              .map(_ => {
                Redirect(routes.AdditionalMetadataSummaryController.getSelectedSummaryPage(consignmentId, metadataType, fileIds))
              })
          }
        }
      } yield result
  }

  private def buildUpdateMetadataInput(updatedFormFields: List[FormField]): List[UpdateFileMetadataInput] = {
    updatedFormFields.flatMap {
      case TextField(fieldId, _, _, multiValue, nameAndValue, _, _, _, _) =>
        UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, nameAndValue.value) :: Nil
      case DateField(fieldId, _, _, multiValue, day, month, year, _, _, _) =>
        val dateTime: LocalDateTime = LocalDate.of(year.value.toInt, month.value.toInt, day.value.toInt).atTime(LocalTime.MIDNIGHT)
        UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, Timestamp.valueOf(dateTime).toString) :: Nil
      case RadioButtonGroupField(fieldId, _, _, _, multiValue, _, selectedOption, _, _, dependencies, _) =>
        val fileMetadataInputs = dependencies.get(selectedOption).map(buildUpdateMetadataInput).getOrElse(Nil)
        UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, stringToBoolean(selectedOption).toString) :: fileMetadataInputs
      case DropdownField(fieldId, _, _, multiValue, _, selectedOption, _, _) =>
        UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, selectedOption.map(_.value).getOrElse("")) :: Nil
    }
  }

  private def deleteDependencyProperties(updatedFormFields: List[FormField], fileIds: List[UUID])(implicit request: Request[AnyContent]): Unit = {

    val propertyNames = updatedFormFields.flatMap {
      case RadioButtonGroupField(_, _, _, _, _, _, selectedOption, _, _, dependencies, _) =>
        dependencies.removed(selectedOption).flatMap { case (_, fields) => fields.map(_.fieldId) }.toList
      case _ => Nil
    }
    if (propertyNames.nonEmpty) {
      customMetadataService.deleteMetadata(fileIds, request.token.bearerAccessToken, Some(propertyNames))
    }
  }

  private def getFormFields(consignmentId: UUID, request: Request[AnyContent], metadataType: String): Future[List[FormField]] = {
    val closure: Boolean = metadataType == "closure"
    for {
      displayProperties <-
        if (closure) { Future(List()) }
        else {
          displayPropertiesService.getDisplayProperties(consignmentId, request.token.bearerAccessToken)
        }
      customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
      formFields =
        if (closure) {
          val customMetadataUtils = new CustomMetadataUtils(customMetadata)
          val dependencyProperties: Set[CustomMetadata] = getDependenciesForValue(customMetadataUtils, closureType.name, closureType.value)
          customMetadataUtils.convertPropertiesToFormFields(dependencyProperties)
        } else {
          val descriptiveProperties = displayProperties.filter(dp => dp.active && dp.propertyType.toLowerCase == metadataType)
          new DisplayPropertiesUtils(descriptiveProperties, customMetadata).convertPropertiesToFormFields.toList
        }
    } yield {
      cache.set("formFields", formFields, 1.hour)
      formFields
    }
  }

  private def getDependenciesForValue(customMetadataUtils: CustomMetadataUtils, propertyName: String, valueToGetDependenciesFrom: String): Set[CustomMetadata] = {
    val propertyToValues: Map[String, List[CustomMetadata.Values]] = customMetadataUtils.getValuesOfProperties(Set(propertyName))
    val allValuesForProperty: Seq[CustomMetadata.Values] = propertyToValues(propertyName)
    val values: Seq[CustomMetadata.Values] = allValuesForProperty.filter(_.value == valueToGetDependenciesFrom)
    val dependencyNames: Seq[String] = values.flatMap(_.dependencies.map(_.name))
    customMetadataUtils.getCustomMetadataProperties(dependencyNames.toSet)
  }

  private def stringToBoolean(value: String): Boolean = {
    Try(value.toBoolean) match {
      case Failure(_)     => value == "yes"
      case Success(value) => value
    }
  }

  private def updateFormFields(orderedFieldsForForm: List[FormField], metadataMap: Map[String, FileMetadata]): List[FormField] = {
    val updatedFormFields = orderedFieldsForForm.map {
      case dateField: DateField =>
        metadataMap
          .get(dateField.fieldId)
          .map(metadata => DateField.update(dateField, Timestamp.valueOf(metadata.value).toLocalDateTime))
          .getOrElse(dateField)
      case dropdownField: DropdownField =>
        metadataMap
          .get(dropdownField.fieldId)
          .map(metadata => DropdownField.update(dropdownField, metadata.value))
          .getOrElse(dropdownField)
      case radioButtonGroupField: RadioButtonGroupField =>
        metadataMap
          .get(radioButtonGroupField.fieldId)
          .map(metadata =>
            RadioButtonGroupField
              .update(radioButtonGroupField, stringToBoolean(metadata.value))
              .copy(dependencies = radioButtonGroupField.dependencies.map { case (key, formFields) => key -> updateFormFields(formFields, metadataMap) })
          )
          .getOrElse(radioButtonGroupField)
      case textField: TextField =>
        metadataMap
          .get(textField.fieldId)
          .map(metadata => TextField.update(textField, metadata.value))
          .getOrElse(textField)
    }
    updatedFormFields.map(formFieldOverrides(_, metadataMap))
  }

  implicit class FileHelper(files: List[getConsignmentFilesMetadata.GetConsignment.Files]) {
    def toFiles: List[File] = files.map(file => {
      val filePath = file.fileMetadata.find(_.name == clientSideOriginalFilepath).map(_.value).getOrElse("")
      File(file.fileId, filePath)
    })
  }
}

object AddAdditionalMetadataController {
  case class File(fileId: UUID, name: String)

  def formFieldOverrides(formField: FormField, fileMetadata: Map[String, FileMetadata]): FormField = {

    // We have hard code this logic here as we are still not sure how to make it data-driven.
    if (formField.fieldId == descriptionClosed) {
      // Hide DescriptionClosed field if the Description property value is empty
      val value = fileMetadata.get(description).map(_.value).getOrElse("")
      val (fieldDescription, hideInputs, info) = if (value.isEmpty) {
        ("There is no description associated with this record. You can add a description in the <strong>Descriptive metadata</strong> section.", true, "")
      } else {
        ("This field cannot be edited here. You can edit the description in the <strong>Descriptive metadata</strong> step.", false, value)
      }
      formField.asInstanceOf[RadioButtonGroupField].copy(fieldDescription = fieldDescription, hideInputs = hideInputs, additionalInfo = info)
    } else {
      formField
    }
  }
}
