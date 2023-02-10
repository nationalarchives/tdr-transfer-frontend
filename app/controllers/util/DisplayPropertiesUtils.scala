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
        case Some(cm) => cm.values.sortBy(_.uiOrdinal).map(v => InputNameAndValue(v.value, v.value))
        case _        => List()
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
    val required: Boolean = customMetadata.requiredField
    property.componentType match {
      case "large text" =>
        TextAreaField(
          property.propertyName,
          property.displayName,
          property.alternativeName,
          property.description,
          property.multiValue,
          InputNameAndValue(property.propertyName, customMetadata.defaultValue),
          required
        )
      case "small text" => generateTextField(property, customMetadata)
      case "date" =>
        DateField(
          property.propertyName,
          property.displayName,
          property.alternativeName,
          property.description,
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
    val dataType = customMetadata match {
      case Some(datatype) => if (datatype.dataType == DataType.Integer) "numeric" else "text"
      case _              => "text"
    }
    val inputName: String = property.guidance
    TextField(
      property.propertyName,
      property.displayName,
      property.alternativeName,
      property.description,
      property.multiValue,
      InputNameAndValue(inputName, customMetadata.defaultValue),
      dataType,
      required
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
      additionalInfo = "",
      property.multiValue,
      Seq(InputNameAndValue(yesLabel, YES), InputNameAndValue(noLabel, NO)),
      defaultOption,
      required,
      dependencies = dependencies
    )
  }
}
