package controllers

import auth.TokenSecurity
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.AddAdditionalMetadataController.{File, formFieldOverrides}
import controllers.util.MetadataProperty.{clientSideOriginalFilepath, description, descriptionClosed}
import controllers.util._
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.types.UpdateFileMetadataInput
import org.pac4j.play.scala.SecurityComponents
import play.api.cache._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.{ConsignmentService, CustomMetadataService, DisplayPropertiesService}
import viewsapi.Caching.preventCaching

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
        consignment <- getConsignmentFileMetadata(consignmentId, metadataType, fileIds)
        formFields <- getFormFields(consignmentId, request, metadataType)
        updatedFormFields <- {
          cache.set(s"$consignmentId-consignment", consignment, 1.hour)
          // Set the values to those of the first file's metadata until we decide what to do with multiple files.
          val metadataMap = consignment.files.headOption.map(_.fileMetadata).getOrElse(Nil).groupBy(_.name).view.mapValues(_.toList).toMap
          Future.successful(updateFormFields(formFields, metadataMap))
        }
      } yield {
        Ok(
          views.html.standard
            .addAdditionalMetadata(consignmentId, consignment.consignmentReference, metadataType, updatedFormFields, request.token.name, consignment.files.toFiles)
        ).uncache()
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
                getConsignmentFileMetadata(consignmentId, metadataType, fileIds)
              }
              metadataMap = consignment.files.headOption.map(_.fileMetadata).getOrElse(Nil).groupBy(_.name).view.mapValues(_.toList).toMap
            } yield {
              val files = consignment.files.toFiles
              BadRequest(
                views.html.standard
                  .addAdditionalMetadata(
                    consignmentId,
                    consignment.consignmentReference,
                    metadataType,
                    updatedFormFields.map(formFieldOverrides(_, metadataMap)),
                    request.token.name,
                    files
                  )
              ).uncache()
            }
          } else {
            updateMetadata(updatedFormFields, consignmentId, fileIds).map { _ =>
              Redirect(routes.AdditionalMetadataSummaryController.getSelectedSummaryPage(consignmentId, metadataType, fileIds))
            }
          }
        }
      } yield result
  }

  private def getDependenciesOfNonSelectedOptions(dependencies: Map[String, List[FormField]], nameOfOptionSelected: List[String]) =
    dependencies.filterNot { case (name, _) => nameOfOptionSelected.contains(name) }.flatMap { case (_, fields) => fields.map(_.fieldId) }

  private def updateMetadata(updatedFormFields: List[FormField], consignmentId: UUID, fileIds: List[UUID])(implicit
      request: Request[AnyContent]
  ): Future[Unit] = {
    val updateMetadataInputs: List[UpdateFileMetadataInput] = buildUpdateMetadataInput(updatedFormFields)

    val deleteMetadataNames: Set[String] = updatedFormFields.flatMap {
      case p if !updateMetadataInputs.exists(_.filePropertyName == p.fieldId) => p.fieldId :: Nil
      case f: FormField                                                       => getDependenciesOfNonSelectedOptions(f.dependencies, f.selectedOptionNames())
      case _ @fieldTypeWithNoImplementation if fieldTypeWithNoImplementation.dependencies.nonEmpty =>
        throw new Exception(s"Dependencies exist for ${fieldTypeWithNoImplementation.fieldId} but there is no implementation to extract them for deletion.")
    }.toSet

    for {
      _ <- if (deleteMetadataNames.nonEmpty) customMetadataService.deleteMetadata(fileIds, request.token.bearerAccessToken, deleteMetadataNames) else Future.successful(())
      _ <- customMetadataService.saveMetadata(consignmentId, fileIds, request.token.bearerAccessToken, updateMetadataInputs)
    } yield ()
  }

  private def getConsignmentFileMetadata(consignmentId: UUID, metadataType: String, fileIds: List[UUID])(implicit request: Request[AnyContent]): Future[GetConsignment] = {
    val additionalProperties = metadataType match {
      case "closure" => Some(List(clientSideOriginalFilepath, description))
      case _         => None
    }
    consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, Some(metadataType), Some(fileIds), additionalProperties)
  }

  private def buildUpdateMetadataInput(updatedFormFields: List[FormField]): List[UpdateFileMetadataInput] = {
    updatedFormFields.collect {
      case TextField(fieldId, _, _, _, multiValue, nameAndValue, _, _, _, _, _, _) if nameAndValue.value.nonEmpty =>
        UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, nameAndValue.value) :: Nil
      case TextAreaField(fieldId, _, _, _, multiValue, nameAndValue, _, _, _, _, _, _) if nameAndValue.value.nonEmpty =>
        UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, nameAndValue.value) :: Nil
      case DateField(fieldId, _, _, _, multiValue, day, month, year, _, _, _, _) if day.value.nonEmpty =>
        val dateTime: LocalDateTime = LocalDate.of(year.value.toInt, month.value.toInt, day.value.toInt).atTime(LocalTime.MIDNIGHT)
        UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, Timestamp.valueOf(dateTime).toString) :: Nil
      case RadioButtonGroupField(fieldId, _, _, _, multiValue, _, selectedOption, _, _, _, dependencies) =>
        val fileMetadataInputs = dependencies.get(selectedOption).map(buildUpdateMetadataInput).getOrElse(Nil)
        UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, stringToBoolean(selectedOption).toString) :: fileMetadataInputs
      case MultiSelectField(fieldId, _, _, _, multiValue, _, selectedOptions, _, _, _) if selectedOptions.isDefined =>
        selectedOptions.getOrElse(Nil).map(p => UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, p.value))
    }.flatten
  }

  private def getFormFields(consignmentId: UUID, request: Request[AnyContent], metadataType: String): Future[List[FormField]] = {
    val closure: Boolean = metadataType == "closure"
    for {
      displayProperties <- displayPropertiesService.getDisplayProperties(consignmentId, request.token.bearerAccessToken, Some(metadataType))
      customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
      formFields =
        if (closure) {
          new DisplayPropertiesUtils(displayProperties, customMetadata).convertPropertiesToFormFields(displayProperties.filter(_.group == "2")).toList
        } else {
          new DisplayPropertiesUtils(displayProperties, customMetadata).convertPropertiesToFormFields().toList
        }
    } yield {
      cache.set("formFields", formFields, 1.hour)
      formFields
    }
  }

  private def stringToBoolean(value: String): Boolean = {
    Try(value.toBoolean) match {
      case Failure(_)     => value == "yes"
      case Success(value) => value
    }
  }

  private def updateFormFields(orderedFieldsForForm: List[FormField], metadataMap: Map[String, List[FileMetadata]]): List[FormField] = {
    val updatedFormFields = orderedFieldsForForm.map {
      case dateField: DateField =>
        metadataMap
          .get(dateField.fieldId)
          .map(metadata => DateField.update(dateField, Timestamp.valueOf(metadata.head.value).toLocalDateTime))
          .getOrElse(dateField)
      case dropdownField: DropdownField =>
        metadataMap
          .get(dropdownField.fieldId)
          .map(metadata => DropdownField.update(dropdownField, metadata.headOption.map(_.value)))
          .getOrElse(dropdownField)
      case multiSelectField: MultiSelectField =>
        metadataMap
          .get(multiSelectField.fieldId)
          .map(metadata => MultiSelectField.update(multiSelectField, metadata.map(_.value)))
          .getOrElse(multiSelectField)
      case radioButtonGroupField: RadioButtonGroupField =>
        metadataMap
          .get(radioButtonGroupField.fieldId)
          .map(metadata =>
            RadioButtonGroupField
              .update(radioButtonGroupField, stringToBoolean(metadata.head.value))
              .copy(dependencies = radioButtonGroupField.dependencies.map { case (key, formFields) => key -> updateFormFields(formFields, metadataMap) })
          )
          .getOrElse(radioButtonGroupField)
      case textField: TextField =>
        metadataMap
          .get(textField.fieldId)
          .map(metadata => TextField.update(textField, metadata.head.value))
          .getOrElse(textField)
      case textAreaField: TextAreaField =>
        metadataMap
          .get(textAreaField.fieldId)
          .map(metadata => TextAreaField.update(textAreaField, metadata.head.value))
          .getOrElse(textAreaField)
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

  def formFieldOverrides(formField: FormField, fileMetadata: Map[String, List[FileMetadata]]): FormField = {

    // We have hard code this logic here as we are still not sure how to make it data-driven.
    if (formField.fieldId == descriptionClosed) {
      // Hide DescriptionClosed field if the Description property value is empty
      val value = fileMetadata.get(description).map(_.head.value).getOrElse("")
      val (fieldDescription, hideInputs, info) = if (value.isEmpty) {
        ("If you need to add a description, you can do so in the Descriptive metadata step.", true, "")
      } else {
        ("The current description of your record is below. You can edit it in the Descriptive metadata step.", false, value)
      }
      formField.asInstanceOf[RadioButtonGroupField].copy(fieldDescription = fieldDescription, hideInputs = hideInputs, additionalInfo = info)
    } else {
      formField
    }
  }
}
