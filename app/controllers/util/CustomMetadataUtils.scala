package controllers.util

import controllers.util.CustomMetadataUtils.FieldValues
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType
import graphql.codegen.types.DataType._
import graphql.codegen.types.PropertyType.{Defined, Supplied}

class CustomMetadataUtils(allCustomMetadataProperties: List[CustomMetadata]) {
  private val allCustomMetadataPropertiesByName: Map[String, List[CustomMetadata]] = allCustomMetadataProperties.groupBy(_.name)

  def getCustomMetadataProperties(propertiesToGet: Set[String]): Set[CustomMetadata] =
    propertiesToGet.flatMap(property => allCustomMetadataPropertiesByName(property))

  def getValuesOfProperties(namesOfPropertiesToGetValuesFrom: Set[String]): Map[String, List[CustomMetadata.Values]] = {
    val propertiesToGetValuesFrom: Set[CustomMetadata] = getCustomMetadataProperties(namesOfPropertiesToGetValuesFrom)
    propertiesToGetValuesFrom.map(property => property.name -> property.values).toMap
  }

  def convertPropertiesToFields(dependencyProperties: Set[CustomMetadata]): List[(FieldValues, String)] = {
    dependencyProperties.toList.sortBy(_.ordinal).map {
      dependencyProperty: CustomMetadata => {
        val (options, selectedOption) = generateFieldOptions(dependencyProperty, dependencyProperty.dataType)
        FieldValues(
          dependencyProperty.name.toLowerCase(),
          fieldOptions=options,
          selectedFieldOption=selectedOption,
          multiValueSelect=dependencyProperty.multiValue,
          fieldLabel=convertDbNameToFieldLabel(dependencyProperty.fullName.getOrElse("")),
          fieldHint=getFieldHints(dependencyProperty.name, dependencyProperty.description.getOrElse("")
          ), // <- Use this until descriptions are added, then use dependencyProperty.description.getOrElse("")
          fieldRequired=dependencyProperty.propertyGroup.getOrElse("").startsWith("Mandatory")
        ) ->
        dependencyProperty.dataType.toString
      }
    }
  }

  private def generateFieldOptions(property: CustomMetadata, dataType: DataType): (Seq[(String, String)], Option[Seq[(String, String)]]) =
    dataType match {
      case Boolean =>
        val radioOptions = Seq(("Yes", "yes"), ("No", "no"))
        val defaultValue = property.defaultValue match {
          case Some("True") => Some("yes")
          case _ => Some("no")
        }
        val optionThatShouldBeSelected = getDefaultValue(radioOptions, defaultValue)
        (radioOptions, optionThatShouldBeSelected)
      case DateTime =>
        val blankDates = Seq(("Day", "DD"), ("Month", "MM"), ("Year", "YYYY"))
        (blankDates, Some(blankDates))
      case Integer =>
        val numberBoxSuffixAndValue = Seq(("years", property.defaultValue.getOrElse("0")))
        val numberBoxSuffixAndValueThatShouldBePresent = Seq(("years", "0"))
        (numberBoxSuffixAndValue, Some(numberBoxSuffixAndValueThatShouldBePresent))
      case Text =>
        if(property.propertyType == Defined) {
          val options: Seq[(String, String)] = property.values.map(valueObject => (valueObject.value, valueObject.value))
          val optionThatShouldBeSelected: Option[Seq[(String, String)]] = getDefaultValue(options, property.defaultValue)
          (options, optionThatShouldBeSelected)
        } else if(property.propertyType == Supplied) {
          (Seq(), None)
        } else {
          (Seq((property.name, property.fullName.getOrElse(""))), None)
        }
      // We don't have any examples of Decimal yet, so this is in the case Decimal or something else gets used
      case _ => throw new IllegalArgumentException(s"$dataType is not a supported dataType")
    }

  private def getDefaultValue(options: Seq[(String, String)], defaultValue: Option[String]): Option[Seq[(String, String)]] =
    defaultValue.map(defaultVal =>
      options.filter {
        case (_, value) => value == defaultVal.toLowerCase()
      }
    )

  private def convertDbNameToFieldLabel(dbName: String): String = {
    val dbAndFieldLabel = Map(
      "Foi Exemption Asserted" -> "FOI decision asserted",
      "Closure Start Date" -> "Closure start date",
      "Closure Period" -> "Closure period",
      "Foi Exemption Code" -> "FOI exemption code",
      "Title Public" -> "Is the title closed?"
    )
    dbAndFieldLabel.getOrElse(dbName, dbName)
  }

  private def getFieldHints(fieldId: String, defaultHint: String): String = {
    val fieldLabelAndHints = Map(
      "FoiExemptionAsserted" -> "Date of the Advisory Council Approval",
      "ClosureStartDate" -> "Date of the record from when the closure starts. It is usually the last date modified.",
      "ClosurePeriod" -> "Number of years the record is closed from the closure start date",
      "FoiExemptionCode" -> "Select the exemption code that applies"
    )
    fieldLabelAndHints.getOrElse(fieldId, defaultHint)
  }
}

object CustomMetadataUtils {
  def apply(allCustomMetadataProperties: List[CustomMetadata]): CustomMetadataUtils = new CustomMetadataUtils(allCustomMetadataProperties)

  case class FieldValues(fieldId: String,
                         fieldOptions: Seq[(String, String)],
                         selectedFieldOption: Option[Seq[(String, String)]],
                         multiValueSelect: Boolean,
                         fieldLabel: String,
                         fieldHint: String,
                         fieldRequired: Boolean=true,
                         fieldError: List[String]=Nil)
}
