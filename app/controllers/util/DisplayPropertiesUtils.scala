package controllers.util

import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType.Text
import graphql.codegen.types.PropertyType.{Defined, Supplied}
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
      case Some(cm) => cm.propertyGroup.contains("MandatoryClosure") || cm.propertyGroup.contains("MandatoryDescriptive")
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
    property.dataType match {
      case Text => textFieldHandler(property, customMetadata)
      case _    => throw new IllegalArgumentException(s"${property.dataType} is not a supported dataType")
    }
  }

  private def textFieldHandler(property: DisplayProperty, customMetadata: Option[CustomMetadata]): FormField = {
    val required = customMetadata.requiredField
    customMetadata.get.propertyType match {
      case Defined =>
        DropdownField(
          property.propertyName,
          property.label,
          property.description,
          property.multiValue,
          customMetadata.definedInputs,
          customMetadata.defaultInput,
          required
        )
      case Supplied =>
        TextField(
          property.propertyName,
          property.label,
          property.description,
          property.multiValue,
          InputNameAndValue(property.propertyName, customMetadata.defaultValue),
          "text",
          required
        )
      case _ =>
        DropdownField(
          property.propertyName,
          property.label,
          property.description,
          property.multiValue,
          Seq(InputNameAndValue(property.propertyName, property.displayName)),
          None,
          required
        )
    }
  }
}
