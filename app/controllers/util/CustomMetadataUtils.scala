package controllers.util

import controllers.util.MetadataProperty._
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType
import graphql.codegen.types.DataType._
import graphql.codegen.types.PropertyType.{Defined, Supplied}

class CustomMetadataUtils(allCustomMetadataProperties: List[CustomMetadata]) {
  private val allCustomMetadataPropertiesByName: Map[String, List[CustomMetadata]] = allCustomMetadataProperties.groupBy(_.name)

  val dbAndFieldLabel: Map[String, String] = Map(
    foiExemptionAsserted -> "FOI decision asserted",
    closureStartDate -> "Closure start date",
    closurePeriod -> "Closure period",
    foiExemptionCode -> "FOI exemption code",
    titlePublic -> "Is the title closed?"
  )

  val dbAndFieldDescription: Map[String, String] = Map(
    foiExemptionAsserted -> "Date of the Advisory Council Approval",
    closureStartDate -> "Date of the record from when the closure starts. It is usually the last date modified.",
    closurePeriod -> "Number of years the record is closed from the closure start date",
    foiExemptionCode -> "Select the exemption code that applies"
  )

  def getCustomMetadataProperties(propertiesToGet: Set[String]): Set[CustomMetadata] =
    propertiesToGet.flatMap(property => allCustomMetadataPropertiesByName(property))

  def getValuesOfProperties(namesOfPropertiesToGetValuesFrom: Set[String]): Map[String, List[CustomMetadata.Values]] = {
    val propertiesToGetValuesFrom: Set[CustomMetadata] = getCustomMetadataProperties(namesOfPropertiesToGetValuesFrom)
    propertiesToGetValuesFrom.map(property => property.name -> property.values).toMap
  }

  def convertPropertiesToFormFields(dependencyProperties: Set[CustomMetadata]): List[FormField] = {
    dependencyProperties.toList.sortBy(_.ordinal).map(generateFieldOptions)
  }

  private def generateFieldOptions(property: CustomMetadata): FormField = {

//    Use this until descriptions are added, then use property.description.getOrElse("")
    val fieldLabel = dbAndFieldLabel.getOrElse(property.name, property.fullName.getOrElse(""))
    val fieldDescription = dbAndFieldDescription.getOrElse(property.name, property.description.getOrElse(""))
    val isRequired = property.propertyGroup.exists(_.startsWith("Mandatory"))
    property.dataType match {
      case Boolean =>
        val selectedOption = property.defaultValue.map(v => if (v == "True") "yes" else "no").getOrElse("no")
        RadioButtonGroupField(property.name, fieldLabel, fieldDescription,
          Seq(InputNameAndValue("Yes", "yes"), InputNameAndValue("No", "no")),
          selectedOption, isRequired
        )
      case DateTime =>
        DateField(property.name, fieldLabel, fieldDescription,
          InputNameAndValue("Day", "", "DD"), InputNameAndValue("Month", "", "MM"), InputNameAndValue("Year", "", "YYYY"),
          isRequired
        )
      case Integer =>
        TextField(property.name, fieldLabel, fieldDescription,
          InputNameAndValue("years", property.defaultValue.getOrElse("")),
          "numeric", isRequired
        )
      case Text =>
        property.propertyType match {
          case Defined =>
            DropdownField(property.name, fieldLabel, fieldDescription,
              property.values.map(v => InputNameAndValue(v.value, v.value)),
              property.defaultValue.map(value => InputNameAndValue(value, value)), isRequired
            )
          case Supplied =>
            DropdownField(property.name, fieldLabel, fieldDescription,
              Seq(),
              property.defaultValue.map(value => InputNameAndValue(value, value)), isRequired
            )
          case _ =>
            DropdownField(property.name, fieldLabel, fieldDescription,
              Seq(InputNameAndValue(property.name, property.fullName.getOrElse(""))),
              None, isRequired
            )
        }
      // We don't have any examples of Decimal yet, so this is in the case Decimal or something else gets used
      case _ => throw new IllegalArgumentException(s"${property.dataType} is not a supported dataType")
    }
  }
}

object CustomMetadataUtils {
  def apply(allCustomMetadataProperties: List[CustomMetadata]): CustomMetadataUtils = new CustomMetadataUtils(allCustomMetadataProperties)
}

object MetadataProperty {
  val foiExemptionAsserted = "FoiExemptionAsserted"
  val closureStartDate = "ClosureStartDate"
  val closurePeriod = "ClosurePeriod"
  val foiExemptionCode = "FoiExemptionCode"
  val titlePublic = "TitlePublic"
}
