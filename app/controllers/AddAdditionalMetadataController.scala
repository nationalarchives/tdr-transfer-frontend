package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import controllers.AddAdditionalMetadataController.{File, formFieldOverrides}
import controllers.util.MetadataProperty._
import controllers.util._
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import graphql.codegen.types.UpdateFileMetadataInput
import org.pac4j.play.scala.SecurityComponents
import play.api.cache._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, CustomMetadataService, DisplayPropertiesService}
import uk.gov.nationalarchives.tdr.validation.MetadataValidation
import viewsapi.Caching.preventCaching

import java.sql.Timestamp
import java.text.SimpleDateFormat
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
    val applicationConfig: ApplicationConfig,
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
          val fileExtension = consignment.files.getFileExtension
          Future.successful(updateFormFields(formFields, metadataMap, fileExtension))
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
        consignment <- cache.getOrElseUpdate[GetConsignment](s"$consignmentId-consignment") {
          getConsignmentFileMetadata(consignmentId, metadataType, fileIds)
        }
        metadataMap = consignment.files.headOption.map(_.fileMetadata).getOrElse(Nil).groupBy(_.name).view.mapValues(_.toList).toMap
        updatedFormFields <- updateAndValidateFormFields(consignmentId, formFields, metadataMap, metadataType)(request)
        result <- {
          if (updatedFormFields.exists(_.fieldErrors.nonEmpty)) {
            val files = consignment.files.toFiles
            val fileExtension = consignment.files.getFileExtension
            Future.successful(
              BadRequest(
                views.html.standard
                  .addAdditionalMetadata(
                    consignmentId,
                    consignment.consignmentReference,
                    metadataType,
                    updatedFormFields.map(formFieldOverrides(_, metadataMap, fileExtension)),
                    request.token.name,
                    files
                  )
              ).uncache()
            )
          } else {
            updateMetadata(updatedFormFields, consignmentId, fileIds).map(_ =>
              Redirect(routes.AdditionalMetadataSummaryController.getSelectedSummaryPage(consignmentId, metadataType, fileIds, Some("review")))
            )
          }
        }
      } yield {
        result
      }
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
      _ <-
        if (deleteMetadataNames.nonEmpty) customMetadataService.deleteMetadata(fileIds, request.token.bearerAccessToken, deleteMetadataNames, consignmentId)
        else Future.successful(())
      _ <- customMetadataService.saveMetadata(consignmentId, fileIds, request.token.bearerAccessToken, updateMetadataInputs)
    } yield ()
  }

  private def getConsignmentFileMetadata(consignmentId: UUID, metadataType: String, fileIds: List[UUID])(implicit request: Request[AnyContent]): Future[GetConsignment] = {
    val additionalProperties = metadataType match {
      case "closure" => Some(List(clientSideOriginalFilepath, description, clientSideFileLastModifiedDate, end_date, fileName))
      case _         => None
    }
    consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, Some(metadataType), Some(fileIds), additionalProperties)
  }

  private def getMetadataValidation(consignmentId: UUID)(implicit request: Request[AnyContent]): Future[MetadataValidation] = {
    for {
      displayProperties <- displayPropertiesService.getDisplayProperties(consignmentId, request.token.bearerAccessToken, None)
      customMetadata <- customMetadataService.getCustomMetadata(consignmentId, request.token.bearerAccessToken)
    } yield {
      MetadataValidationUtils.createMetadataValidation(displayProperties, customMetadata)
    }
  }

  private def updateAndValidateFormFields(consignmentId: UUID, defaultFieldValues: List[FormField], metadataMap: Map[String, List[FileMetadata]], metadataType: String)(
      request: Request[AnyContent]
  ): Future[List[FormField]] = {
    if (applicationConfig.blockValidationLibrary) {
      val dynamicFormUtils = new DynamicFormUtils(request, defaultFieldValues, None)
      Future.successful(dynamicFormUtils.convertSubmittedValuesToFormFields(dynamicFormUtils.formAnswersWithValidInputNames))
    } else {
      for {
        metadataValidation <- cache.getOrElseUpdate[MetadataValidation]("metadataValidation") {
          getMetadataValidation(consignmentId)(request)
        }
      } yield {
        val dynamicFormUtils = new DynamicFormUtils(request, defaultFieldValues, Some(metadataValidation))
        dynamicFormUtils.updateAndValidateFormFields(dynamicFormUtils.formAnswersWithValidInputNames, metadataMap, metadataType)
      }
    }
  }

  private def buildUpdateMetadataInput(updatedFormFields: List[FormField]): List[UpdateFileMetadataInput] = {
    updatedFormFields.collect {
      case TextField(fieldId, _, _, _, _, multiValue, nameAndValue, _, _, _, _, _, _) if nameAndValue.value.nonEmpty =>
        UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, nameAndValue.value) :: Nil
      case TextAreaField(fieldId, _, _, _, _, multiValue, nameAndValue, _, _, _, _, _, _, _) if nameAndValue.value.nonEmpty =>
        UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, nameAndValue.value) :: Nil
      case DateField(fieldId, _, _, _, _, multiValue, day, month, year, _, _, _, _) if day.value.nonEmpty =>
        val dateTime: LocalDateTime = LocalDate.of(year.value.toInt, month.value.toInt, day.value.toInt).atTime(LocalTime.MIDNIGHT)
        UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, Timestamp.valueOf(dateTime).toString) :: Nil
      case RadioButtonGroupField(fieldId, _, _, _, _, _, multiValue, _, selectedOption, _, _, _, dependencies) =>
        val fileMetadataInputs = dependencies.get(selectedOption).map(buildUpdateMetadataInput).getOrElse(Nil)
        UpdateFileMetadataInput(filePropertyIsMultiValue = multiValue, fieldId, stringToBoolean(selectedOption).toString) :: fileMetadataInputs
      case MultiSelectField(fieldId, _, _, _, _, multiValue, _, selectedOptions, _, _, _) if selectedOptions.isDefined =>
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

  private def updateFormFields(orderedFieldsForForm: List[FormField], metadataMap: Map[String, List[FileMetadata]], fileExtension: String): List[FormField] = {
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
              .copy(dependencies = radioButtonGroupField.dependencies.map { case (key, formFields) => key -> updateFormFields(formFields, metadataMap, fileExtension) })
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
    updatedFormFields.map(formFieldOverrides(_, metadataMap, fileExtension))
  }

  implicit class FileHelper(files: List[getConsignmentFilesMetadata.GetConsignment.Files]) {
    def toFiles: List[File] = files.map(file => {
      val filePath = file.fileMetadata.find(_.name == clientSideOriginalFilepath).map(_.value).getOrElse("")
      File(file.fileId, filePath)
    })

    def getFileExtension: String = {
      val pattern = """[.][a-zA-Z]+""".r
      files.head.fileName.flatMap(f => pattern.findAllIn(f).toList.lastOption).getOrElse("")
    }
  }
}

object AddAdditionalMetadataController {
  case class File(fileId: UUID, name: String)
  private val dateModifiedInsetText =
    "The date the record was last modified was determined during upload. This date should be checked against your own records: <strong>%s</strong>"

  def formFieldOverrides(formField: FormField, fileMetadata: Map[String, List[FileMetadata]], fileExtension: String): FormField = {
    formField.fieldId match {
      case `descriptionClosed` => overrideClosedText(formField, fileMetadata.get(description), description)
      case `titleClosed`       => overrideClosedText(formField, fileMetadata.get(fileName), title)
      case `end_date`          => overrideEndDate(formField, fileMetadata)
      case `closureStartDate`  => overrideClosureStartDate(formField, fileMetadata)
      case `titleAlternate`    => overrideTitleAlternate(formField, fileExtension: String)
      case _                   => formField
    }
  }

  private def overrideClosedText(formField: FormField, metadataValue: Option[List[FileMetadata]], fieldName: String): FormField = {
    // We have hard code this logic here as we are still not sure how to make it data-driven.
    // Hide field if the property value is empty
    val value = metadataValue.flatMap(_.headOption).map(_.value).getOrElse("")
    val (fieldDescription, hideInputs, info) = if (value.isEmpty) {
      (
        s"If the $fieldName of this record contains sensitive information you must add an uncensored description in Descriptive Metadata section before entering an alternative $fieldName here.",
        true,
        ""
      )
    } else {
      (s"If the $fieldName of this record contains sensitive information, you must select 'Yes' and provide an alternative $fieldName.", false, value)
    }
    formField.asInstanceOf[RadioButtonGroupField].copy(fieldDescription = fieldDescription, hideInputs = hideInputs, additionalInfo = info)
  }

  private def overrideEndDate(formField: FormField, fileMetadata: Map[String, List[FileMetadata]]) = {
    val lastModifiedDate = fileMetadata.get(clientSideFileLastModifiedDate).map(_.head.value).getOrElse("")
    val formattedLastModifiedDate = dateFormatter(lastModifiedDate)
    formField
      .asInstanceOf[DateField]
      .copy(fieldInsetTexts =
        List(
          s"The date the record was last modified was determined during upload. This date should be checked against your own records: <strong>$formattedLastModifiedDate</strong>"
        )
      )
  }

  private def overrideClosureStartDate(formField: FormField, fileMetadata: Map[String, List[FileMetadata]]) = {
    val lastModifiedDate = fileMetadata.get(clientSideFileLastModifiedDate).map(_.head.value).getOrElse("")
    val formattedLastModifiedDate = dateFormatter(lastModifiedDate)
    val endDate = fileMetadata.get(end_date).map(_.head.value).getOrElse("")
    val insetTexts: List[String] = if (endDate.isEmpty) {
      List(dateModifiedInsetText.format(formattedLastModifiedDate))
    } else {
      val formattedEndDate = dateFormatter(endDate)
      List(s"The date of the last change to this record entered as descriptive metadata is <strong>$formattedEndDate</strong>")
    }
    formField.asInstanceOf[DateField].copy(fieldInsetTexts = insetTexts)
  }

  private def overrideTitleAlternate(formField: FormField, fileExtension: String): FormField = {
    formField.asInstanceOf[TextField].copy(suffixText = Some(fileExtension))
  }

  private def dateFormatter(currentDateFormat: String, newDateFormat: String = "dd/MM/yyyy") = {
    val formatter = new SimpleDateFormat(newDateFormat)
    if (currentDateFormat.nonEmpty) formatter.format(Timestamp.valueOf(currentDateFormat)) else ""
  }
}
