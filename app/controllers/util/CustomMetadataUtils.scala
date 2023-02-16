package controllers.util

import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values

class CustomMetadataUtils(allCustomMetadataProperties: List[CustomMetadata]) {
  private val allCustomMetadataPropertiesByName: Map[String, List[CustomMetadata]] = allCustomMetadataProperties.groupBy(_.name)

  def getCustomMetadataProperties(propertiesToGet: Set[String]): Set[CustomMetadata] =
    propertiesToGet.flatMap(property => allCustomMetadataPropertiesByName(property))

  def getValuesOfProperties(namesOfPropertiesToGetValuesFrom: Set[String]): Map[String, List[Values]] = {
    val propertiesToGetValuesFrom: Set[CustomMetadata] = getCustomMetadataProperties(namesOfPropertiesToGetValuesFrom)
    propertiesToGetValuesFrom.map(property => property.name -> property.values).toMap
  }
}

object CustomMetadataUtils {
  def apply(allCustomMetadataProperties: List[CustomMetadata]): CustomMetadataUtils = new CustomMetadataUtils(allCustomMetadataProperties)
}

case class StaticMetadata(name: String, value: String)

object MetadataProperty {
  val foiExemptionAsserted = "FoiExemptionAsserted"
  val closureStartDate = "ClosureStartDate"
  val closurePeriod = "ClosurePeriod"
  val foiExemptionCode = "FoiExemptionCode"
  val titleClosed = "TitleClosed"
  val descriptionClosed = "DescriptionClosed"
  val clientSideOriginalFilepath = "ClientSideOriginalFilepath"
  val descriptionPublic = "DescriptionPublic"
  val titleAlternate = "TitleAlternate"
  val descriptionAlternate = "DescriptionAlternate"
  val description = "description"
  val fileType = "FileType"
  val closureType: StaticMetadata = StaticMetadata("ClosureType", "Closed")
  val descriptiveType: StaticMetadata = StaticMetadata("DescriptiveType", "")
  val clientSideFileLastModifiedDate = "ClientSideFileLastModifiedDate"
  val end_date = "end_date"
}
