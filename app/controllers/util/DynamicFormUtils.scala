package controllers.util

import controllers.util.MetadataProperty.closureType
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
import play.api.mvc.{AnyContent, Request}
import uk.gov.nationalarchives.tdr.validation.{Metadata, MetadataValidation}

class DynamicFormUtils(request: Request[AnyContent], defaultFieldValues: List[FormField], metadataValidation: Option[MetadataValidation] = None) {
  type InputName = String
  type OptionSelected = String

  private val formAnswers: Map[InputName, Seq[OptionSelected]] = request.body.asFormUrlEncoded match {
    case Some(answers: Map[InputName, Seq[OptionSelected]]) => answers
    case _                                                  => throw new Exception("Error: There were no values submitted.") // This should never happen
  }

  lazy val formAnswersWithValidInputNames: Map[InputName, Seq[OptionSelected]] = formAnswers.filter {
    case (inputName, _) if inputName.startsWith("input")          => true
    case (inputName, _) if inputName == "csrfToken"               => false
    case (inputName, _) if inputName == "tna-multi-select-search" => false
    case (inputName, _) if inputName == "details"                 => false
    case (inputName, _)                                           => throw new IllegalArgumentException(s"${inputName.split("-").head} is not a supported field type.")
  }

  def updateAndValidateFormFields(submittedValues: Map[InputName, Seq[OptionSelected]], metadataMap: Map[String, List[FileMetadata]], metadataType: String): List[FormField] = {

    val submittedValuesTrimmed: List[SubmittedValue] = convertToSubmittedValues(submittedValues)
    val excludeFields: List[String] = submittedValuesTrimmed.collect {
      case submittedValue if submittedValue.optionsSelected.contains("exclude") => submittedValue.inputName
    }

    val updatedFormField = defaultFieldValues.map { formField =>
      val matchingFieldValue: List[SubmittedValue] = getSubmittedFieldValue(formField.fieldId, submittedValuesTrimmed)
      if (excludeFields.contains(matchingFieldValue.head.inputName)) {
        formField
      } else {
        updateFormFields(formField, matchingFieldValue)
      }
    }

    val inputMetadata = updatedFormField
      .filter(f => !excludeFields.exists(_.contains(f.fieldId)))
      .flatMap(field => field.dependencies.values.flatten.toList :+ field)
      .map(f => Metadata(f.fieldId, f.selectedOptions()))

    val errors = metadataValidation match {
      case Some(mv) =>
        if (metadataType == "closure") {
          val closureMetadata = Metadata(closureType.name, metadataMap(closureType.name).head.value)
          mv.validateClosureMetadata(inputMetadata :+ closureMetadata)
        } else {
          mv.validateDescriptiveMetadata(inputMetadata)
        }
      case None => throw new IllegalStateException("metadataValidation object is missing")
    }
    if (errors.exists(_.propertyName == closureType.name)) {
      // Throw an exception if user manually modify the url to load "add closure metadata" page without confirming the closure status
      throw new IllegalStateException("Unexpect error occurred - " + errors.find(_.propertyName == closureType.name).head)
    } else {
      updatedFormField.map(formField => {
        errors
          .find(_.propertyName == formField.fieldId)
          .map(e =>
            formField match {
              case radioField: RadioButtonGroupField  => RadioButtonGroupField.updateError(radioField, e.errorCode)
              case textField: TextField               => TextField.updateError(textField, e.errorCode)
              case textAreaField: TextAreaField       => TextAreaField.updateError(textAreaField, e.errorCode)
              case dateField: DateField               => DateField.updateError(dateField, e.errorCode)
              case multiSelectField: MultiSelectField => MultiSelectField.updateError(multiSelectField, e.errorCode)
              case dropdownField: DropdownField       => DropdownField.updateError(dropdownField, e.errorCode)
              case _                                  => formField
            }
          )
          .getOrElse(formField)
      })
    }
  }

  private def updateFormFields(formField: FormField, fieldValue: List[SubmittedValue]): FormField = {
    val optionsSelected: Seq[InputName] = fieldValue.head.optionsSelected

    formField match {
      case dateField: DateField =>
        val (day, month, year) = fieldValue.toDate
        DateField.update(dateField, day, month, year)

      case radioButtonGroupField: RadioButtonGroupField =>
        val selectedOption = fieldValue.getValue(radioButtonGroupField.fieldId)
        val dependencies = radioButtonGroupField.dependencies
          .get(selectedOption)
          .map(_.map(formField => formField.fieldId -> fieldValue.getValue(s"${radioButtonGroupField.fieldId}-${formField.fieldId}-$selectedOption")).toMap)
          .getOrElse(Map.empty)
        RadioButtonGroupField
          .update(radioButtonGroupField, selectedOption, dependencies)

      case textField: TextField =>
        val text = optionsSelected.head
        TextField
          .update(textField, text)

      case textAreaField: TextAreaField =>
        val text = optionsSelected.head
        TextAreaField
          .update(textAreaField, text)
      case dropdownField: DropdownField =>
        val selectedValue = optionsSelected.headOption
        DropdownField
          .update(dropdownField, selectedValue)

      case multiSelectField: MultiSelectField =>
        val selectedValues = optionsSelected
        MultiSelectField
          .update(multiSelectField, selectedValues)
    }
  }

  private def getSubmittedFieldValue(fieldId: String, submittedValues: List[SubmittedValue]): List[SubmittedValue] = {
    val fieldValue: List[SubmittedValue] = submittedValues.filter(_.inputName.contains(fieldId))
    if (fieldValue.isEmpty) List(SubmittedValue(fieldId, Nil)) else fieldValue
  }

  private def convertToSubmittedValues(submittedValues: Map[InputName, Seq[OptionSelected]]): List[SubmittedValue] =
    submittedValues.map { case (key, values) => SubmittedValue(key, values.map(_.trim)) }.toList

  implicit class FieldValueHelper(fieldValue: List[SubmittedValue]) {

    def toDate: (String, String, String) =
      (getValue("-day"), getValue("-month"), getValue("-year"))

    def getValue(key: String): String = {
      val fieldWithInputNameEndingWithKey: Option[SubmittedValue] = fieldValue.find { _.inputName.endsWith(key) }
      fieldWithInputNameEndingWithKey.flatMap { _.optionsSelected.headOption }.getOrElse("")
    }
  }
}

case class SubmittedValue(inputName: String, optionsSelected: Seq[String])
