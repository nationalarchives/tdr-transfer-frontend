package controllers.util

import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType
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

  def convertPropertiesToFormFields(displayProperties: List[DisplayProperty]): Seq[FormField] = {
    displayProperties
      .sortBy(_.ordinal)
      .map(dp => {
        val metadata: Option[CustomMetadata] = customMetadata.find(_.name == dp.propertyName)
        generateFormField(dp, metadata)
      })
  }
  // scalastyle:off method.length
  private def generateFormField(property: DisplayProperty, customMetadata: Option[CustomMetadata]): FormField = {
    val required: Boolean = customMetadata.requiredField
    property.componentType match {
      case "large text" =>
        TextAreaField(
          property.propertyName,
          property.displayName,
          property.description,
          property.multiValue,
          InputNameAndValue(property.propertyName, customMetadata.defaultValue),
          required
        )
      case "small text" =>
        TextField(
          property.propertyName,
          property.displayName,
          property.description,
          property.multiValue,
          InputNameAndValue(property.propertyName, customMetadata.defaultValue),
          "text",
          required)
      case "date" =>
        DateField(
          property.propertyName,
          property.displayName,
          property.description,
          property.multiValue,
          InputNameAndValue("Day", "", "DD"),
          InputNameAndValue("Month", "", "MM"),
          InputNameAndValue("Year", "", "YYYY"),
          required
        )
      case "integer" =>
        TextField(
          property.propertyName,
          property.displayName,
          property.description,
          property.multiValue,
          InputNameAndValue("years", customMetadata.defaultValue),
          "numeric",
          required
        )
      case "radial" =>
        val selectedOption = if(customMetadata.defaultValue.toBoolean) "yes" else "no"
        val dependencies = customMetadata.get.values
          .map(p => {
            val dependencies = p.dependencies.map(_.name).toSet
            (if (p.value.toBoolean) "yes" else "no") -> convertPropertiesToFormFields(displayProperties.filter(p => dependencies.contains(p.propertyName))).toList
          })
          .toMap
        RadioButtonGroupField(
          property.propertyName,
          property.displayName,
          property.description,
          additionalInfo = "",
          property.multiValue,
          Seq(InputNameAndValue("Yes", "yes"), InputNameAndValue("No", "no")),
          selectedOption,
          required,
          dependencies = dependencies
        )
      case "select" =>
        if (property.multiValue) {
          MultiSelectField(
            property.propertyName,
            property.displayName,
            property.description,
            property.multiValue,
            customMetadata.definedInputs,
            customMetadata.defaultInput.map(List(_)),
            required
          )
        } else {
          DropdownField(
            property.propertyName,
            property.displayName,
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
}
