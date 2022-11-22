package controllers.util

import controllers.util.MetadataProperty._
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType._
import graphql.codegen.types.PropertyType.{Defined, Supplied}

class CustomMetadataUtils(allCustomMetadataProperties: List[CustomMetadata]) {
  private val allCustomMetadataPropertiesByName: Map[String, List[CustomMetadata]] = allCustomMetadataProperties.groupBy(_.name)

  val dbAndFieldLabel: Map[String, String] = Map(
    foiExemptionAsserted -> "FOI decision asserted",
    closureStartDate -> "Closure start date",
    closurePeriod -> "Closure period",
    foiExemptionCode -> "FOI exemption code",
    titleClosed -> "Is the title closed?",
    titleAlternate -> "Alternate Title",
    descriptionAlternate -> "Alternate Description"
  )

  def getCustomMetadataProperties(propertiesToGet: Set[String]): Set[CustomMetadata] =
    propertiesToGet.flatMap(property => allCustomMetadataPropertiesByName(property))

  def getValuesOfProperties(namesOfPropertiesToGetValuesFrom: Set[String]): Map[String, List[CustomMetadata.Values]] = {
    val propertiesToGetValuesFrom: Set[CustomMetadata] = getCustomMetadataProperties(namesOfPropertiesToGetValuesFrom)
    propertiesToGetValuesFrom.map(property => property.name -> property.values).toMap
  }

  def convertPropertiesToFormFields(dependencyProperties: Set[CustomMetadata]): List[FormField] = {
    dependencyProperties.toList.sortBy(_.uiOrdinal).map(generateFieldOptions)
  }

  // scalastyle:off method.length
  // scalastyle:off cyclomatic.complexity
  private def generateFieldOptions(property: CustomMetadata): FormField = {
    val fieldLabel = dbAndFieldLabel.getOrElse(property.name, property.fullName.getOrElse(""))
    val fieldDescription = property.description.getOrElse("")
    val isRequired = property.propertyGroup.exists(_.startsWith("Mandatory"))
    property.dataType match {
      case Boolean =>
        val selectedOption = property.defaultValue.map(v => if (v == "True") "yes" else "no").getOrElse("no")
        RadioButtonGroupField(
          property.name,
          fieldLabel,
          fieldDescription,
          property.multiValue,
          Seq(InputNameAndValue("Yes", "yes"), InputNameAndValue("No", "no")),
          selectedOption,
          isRequired
        )
      case DateTime =>
        DateField(
          property.name,
          fieldLabel,
          fieldDescription,
          property.multiValue,
          InputNameAndValue("Day", "", "DD"),
          InputNameAndValue("Month", "", "MM"),
          InputNameAndValue("Year", "", "YYYY"),
          isRequired,
          isFutureDateAllowed = property.name != foiExemptionAsserted
        )
      case Integer =>
        TextField(property.name, fieldLabel, fieldDescription, property.multiValue, InputNameAndValue("years", property.defaultValue.getOrElse("")), "numeric", isRequired)
      case Text =>
        property.propertyType match {
          case Defined if property.multiValue =>
            CheckboxField(
              property.name,
              fieldLabel,
              fieldDescription,
              property.multiValue,
              property.values.sortBy(_.uiOrdinal).map(v => InputNameAndValue(v.value, v.value)),
              property.defaultValue.map(value => Seq(InputNameAndValue(value, value))),
              isRequired
            )
          case Defined =>
            DropdownField(
              property.name,
              fieldLabel,
              fieldDescription,
              property.multiValue,
              property.values.sortBy(_.uiOrdinal).map(v => InputNameAndValue(v.value, v.value)),
              property.defaultValue.map(value => InputNameAndValue(value, value)),
              isRequired
            )
          case Supplied =>
            TextField(property.name, fieldLabel, fieldDescription, property.multiValue, InputNameAndValue(property.name, property.defaultValue.getOrElse("")), "text", isRequired)
          case _ =>
            DropdownField(
              property.name,
              fieldLabel,
              fieldDescription,
              property.multiValue,
              Seq(InputNameAndValue(property.name, property.fullName.getOrElse(""))),
              None,
              isRequired
            )
        }
      // We don't have any examples of Decimal yet, so this is in the case Decimal or something else gets used
      case _ => throw new IllegalArgumentException(s"${property.dataType} is not a supported dataType")
    }
  }
}
// scalastyle:on method.length
// scalastyle:on cyclomatic.complexity

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
  val clientSideOriginalFilepath = "ClientSideOriginalFilepath"
  val descriptionPublic = "DescriptionPublic"
  val titleAlternate = "TitleAlternate"
  val descriptionAlternate = "DescriptionAlternate"
  val closureType: StaticMetadata = StaticMetadata("ClosureType", "Closed")
}
