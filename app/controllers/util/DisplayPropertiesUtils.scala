package controllers.util

import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType.Text
import services.DisplayProperty

class DisplayPropertiesUtils(displayProperties: List[DisplayProperty], customMetadata: List[CustomMetadata]) {

  implicit class CustomMetadataHelper(customMetadata: Option[CustomMetadata]) {

    def defaultValue: String = {
      customMetadata match {
        case Some(cm) => cm.defaultValue.getOrElse("")
        case _        => ""
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
    val defaultValue = customMetadata.defaultValue
    val required = customMetadata.requiredField
    TextField(
      property.displayName,
      property.label,
      property.description,
      property.multiValue,
      InputNameAndValue(property.propertyName, defaultValue),
      "text",
      required
    )
  }
}
