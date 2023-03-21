package controllers.util

import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType
import org.apache.commons.lang3.BooleanUtils.{YES, NO}
import services.DisplayProperty

class DisplayPropertiesUtils(displayProperties: List[DisplayProperty], customMetadata: List[CustomMetadata]) {

  implicit class CustomMetadataHelper(customMetadata: Option[CustomMetadata]) {
    def defaultValue: String = {
      customMetadata match {
        case Some(cm) => cm.defaultValue.getOrElse("")
        case _        => ""
      }
    }

    def defaultInput: Option[InputNameAndValue] = {
      customMetadata match {
        case Some(cm) => cm.defaultValue.map(value => InputNameAndValue(value, value))
        case _        => None
      }
    }

    def definedInputs: List[InputNameAndValue] = {
      customMetadata match {
        case Some(cm) if cm.values.exists(_.uiOrdinal == Int.MaxValue) => cm.values.sortBy(_.value).map(v => InputNameAndValue(v.value, v.value))
        case Some(cm)                                                  => cm.values.sortBy(_.uiOrdinal).map(v => InputNameAndValue(v.value, v.value))
        case _                                                         => List()
      }
    }

    def requiredField: Boolean = customMetadata match {
      case Some(cm) => cm.propertyGroup.contains("MandatoryClosure") || cm.propertyGroup.contains("MandatoryMetadata")
      case _        => false
    }
  }

  def convertPropertiesToFormFields(displayProperties: List[DisplayProperty] = displayProperties): Seq[FormField] = {
    displayProperties
      .map(dp => {
        val metadata: Option[CustomMetadata] = customMetadata.find(_.name == dp.propertyName)
        generateFormField(dp, metadata)
      })
  }

  private def generateFormField(property: DisplayProperty, customMetadata: Option[CustomMetadata]): FormField = {
    // Always ensure that if required in data schema still required in the UI
    val required: Boolean = if (customMetadata.requiredField) customMetadata.requiredField else property.required

    property.componentType match {
      case "large text" =>
        TextAreaField(
          property.propertyName,
          property.displayName,
          property.alternativeName,
          property.description,
          Nil,
          property.multiValue,
          InputNameAndValue(property.propertyName, customMetadata.defaultValue),
          required,
          details = property.details
        )
      case "small text" => generateTextField(property, customMetadata)
      case "date" =>
        DateField(
          property.propertyName,
          property.displayName,
          property.alternativeName,
          property.description,
          Nil,
          property.multiValue,
          InputNameAndValue("Day", "", "DD"),
          InputNameAndValue("Month", "", "MM"),
          InputNameAndValue("Year", "", "YYYY"),
          required
        )
      case "radial" => generateRadioField(property, customMetadata)
      case "select" =>
        if (property.multiValue) {
          MultiSelectField(
            property.propertyName,
            property.displayName,
            property.alternativeName,
            property.description,
            Nil,
            property.guidance,
            property.multiValue,
            customMetadata.definedInputs,
            customMetadata.defaultInput.map(List(_)),
            required
          )
        } else {
          DropdownField(
            property.propertyName,
            property.displayName,
            property.alternativeName,
            property.description,
            Nil,
            property.multiValue,
            customMetadata.definedInputs,
            customMetadata.defaultInput,
            required
          )
        }
      case _ => throw new IllegalArgumentException(s"${property.componentType} is not a supported component type")
    }
  }

  private def generateTextField(property: DisplayProperty, customMetadata: Option[CustomMetadata]) = {
    val required: Boolean = customMetadata.requiredField
    val (inputMode, inputType) = property.dataType match {
      case DataType.Integer => ("numeric", "number")
      case _                => ("text", "text")
    }
    val inputName: String = property.guidance
    TextField(
      property.propertyName,
      property.displayName,
      property.alternativeName,
      property.description,
      Nil,
      property.multiValue,
      InputNameAndValue(inputName, customMetadata.defaultValue),
      inputMode,
      required,
      addSuffixText = property.guidance.nonEmpty,
      inputType = inputType
    )
  }

  private def generateRadioField(property: DisplayProperty, customMetadata: Option[CustomMetadata]) = {
    val required: Boolean = customMetadata.requiredField
    val defaultOption = if (customMetadata.defaultValue.toBoolean) YES else NO
    val List(yesLabel, noLabel) = property.label.split('|').toList
    val dependencies = customMetadata.get.values
      .map(p => {
        val dependencies = p.dependencies.map(_.name).toSet
        (if (p.value.toBoolean) YES else NO) -> convertPropertiesToFormFields(displayProperties.filter(p => dependencies.contains(p.propertyName))).toList
      })
      .toMap
    RadioButtonGroupField(
      property.propertyName,
      property.displayName,
      property.alternativeName,
      property.description,
      Nil,
      additionalInfo = "",
      property.multiValue,
      Seq(InputNameAndValue(yesLabel, YES), InputNameAndValue(noLabel, NO)),
      defaultOption,
      required,
      dependencies = dependencies
    )
  }
}
