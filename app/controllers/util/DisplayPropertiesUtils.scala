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

  def convertPropertiesToFormFields: Seq[FormField] = {
    displayProperties
      .sortBy(_.ordinal)
      .map(dp => {
        val metadata: Option[CustomMetadata] = customMetadata.find(_.name == dp.propertyName)
        generateFormField(dp, metadata)
      })
  }

  private def generateFormField(property: DisplayProperty, customMetadata: Option[CustomMetadata]): FormField = {
    val required: Boolean = customMetadata.requiredField
    val inputType: String = if (property.dataType == DataType.Integer) { "number" }
    else { "text" }
    property.componentType match {
      // set 'large text' to text field until have text area field
      case "large text" =>
        TextField(
          property.propertyName,
          property.displayName,
          property.description,
          property.multiValue,
          InputNameAndValue(property.propertyName, customMetadata.defaultValue),
          "text",
          required,
          inputType = inputType
        )
      case "select" =>
        DropdownField(
          property.propertyName,
          property.displayName,
          property.description,
          property.multiValue,
          customMetadata.definedInputs,
          customMetadata.defaultInput,
          required
        )
      case _ => throw new IllegalArgumentException(s"${property.componentType} is not a supported component type")
    }
  }
}
