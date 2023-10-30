package controllers.util

import com.github.jknack.handlebars.internal.lang3.BooleanUtils.{NO, YES}
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType.{Boolean, Text}
import services.DisplayProperty
import uk.gov.nationalarchives.tdr.validation.{DataType, MetadataCriteria, MetadataValidation}

object MetadataValidationUtils {

  def createMetadataValidation(displayProperties: List[DisplayProperty], customMetadata: List[CustomMetadata]): MetadataValidation = {
    val closureMetadataCriteria = createCriteria(displayProperties.filter(displayPropertyFilter(_, "closure")).filter(_.group == "1"), customMetadata, displayProperties).head
    val descriptiveMetadataCriteria = createCriteria(displayProperties.filter(displayPropertyFilter(_, "descriptive")), customMetadata, displayProperties)
    new MetadataValidation(closureMetadataCriteria, descriptiveMetadataCriteria)
  }

  private def displayPropertyFilter(dp: DisplayProperty, metadataType: String): Boolean = {
    dp.active && dp.propertyType.equalsIgnoreCase(metadataType)
  }

  private def createCriteria(displayProperties: List[DisplayProperty], allCustomMetadata: List[CustomMetadata], allProperties: List[DisplayProperty]): List[MetadataCriteria] = {
    displayProperties.map(dp => {
      val cm = allCustomMetadata.find(_.name == dp.propertyName)
      val definedValues: List[String] = dp.dataType match {
        case Boolean => List(YES, NO)
        case Text    => cm.map(_.values.map(_.value)).getOrElse(List())
        case _       => List()
      }
      val requiredField: Boolean = cm match {
        case Some(cm) => cm.propertyGroup.contains("MandatoryClosure") || cm.propertyGroup.contains("MandatoryMetadata") || dp.required
        case _        => dp.required
      }
      MetadataCriteria(
        dp.propertyName,
        DataType.get(dp.dataType.toString),
        requiredField,
        isFutureDateAllowed = false,
        isMultiValueAllowed = dp.multiValue,
        definedValues,
        None,
        dependencies =
          cm.map(_.values.map(v => v.value -> createCriteria(allProperties.filter(m => v.dependencies.exists(_.name == m.propertyName)), allCustomMetadata, allProperties)).toMap)
      )
    })
  }
}
