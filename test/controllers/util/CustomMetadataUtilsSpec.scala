package controllers.util

import cats.implicits.catsSyntaxOptionId
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType.{Boolean, DateTime, Integer, Text}
import graphql.codegen.types.PropertyType.{Defined, Supplied}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar
import testUtils.FormTestData

class CustomMetadataUtilsSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
  private val formProperties = new FormTestData().setupCustomMetadata()
  private val customMetadataUtils = CustomMetadataUtils(formProperties)

  "getCustomMetadataProperties" should "return the list of properties requested" in {
    val namesOfPropertiesToGet = formProperties.map(_.name).toSet
    val listOfPropertiesRetrieved: Set[CustomMetadata] = customMetadataUtils.getCustomMetadataProperties(namesOfPropertiesToGet)

    val propertiesRetrievedEqualPropertiesRequested = listOfPropertiesRetrieved.forall(propertyRetrieved => namesOfPropertiesToGet.contains(propertyRetrieved.name))
    propertiesRetrievedEqualPropertiesRequested should equal(true)
  }

  "getCustomMetadataProperties" should "throw an 'NoSuchElementException' if any properties requested are not present" in {
    val namesOfPropertiesToGet = Set("TestProperty11", "TestProperty3")

    val thrownException: NoSuchElementException =
      the[NoSuchElementException] thrownBy customMetadataUtils.getCustomMetadataProperties(namesOfPropertiesToGet)

    thrownException.getMessage should equal("key not found: TestProperty11")
  }

  "getValuesOfProperties" should "return the values for a given property" in {
    val namesOfPropertiesAndTheirExpectedValues = Map(
      "FoiExemptionAsserted" -> List(),
      "Dropdown" -> List("dropdownValue", "dropdownValue2"),
      "Radio" -> List("True", "False")
    )

    val actualPropertiesAndTheirValues: Map[String, List[CustomMetadata.Values]] =
      customMetadataUtils.getValuesOfProperties(namesOfPropertiesAndTheirExpectedValues.keys.toSet)

    namesOfPropertiesAndTheirExpectedValues.foreach { case (propertyName, expectedValues) =>
      actualPropertiesAndTheirValues(propertyName).map(_.value) should equal(expectedValues)
    }
  }

  "getValuesOfProperties" should "throw an 'NoSuchElementException' if any properties (from which to obtain values from) are not present" in {
    val namesOfPropertiesToGet = Set("FoiExemptionAsserted", "UnknownProperty")

    val thrownException: NoSuchElementException =
      the[NoSuchElementException] thrownBy customMetadataUtils.getCustomMetadataProperties(namesOfPropertiesToGet)

    thrownException.getMessage should equal("key not found: UnknownProperty")
  }

  "convertPropertiesToFields" should "convert properties and its dependencies to fields for the form, if given correctly formatted properties" in {
    val propertiesToConvertToFields: Set[CustomMetadata] = formProperties.toSet
    val fieldValuesByDataType: List[FormField] = customMetadataUtils.convertPropertiesToFormFields(propertiesToConvertToFields)

    propertiesToConvertToFields.foreach { property => checkMetadataToFieldConversion(property, fieldValuesByDataType) }

  }

  "convertPropertiesToFields" should "convert properties to fields for the form when the given properties don't have default values" in {
    val allPropertiesWithoutDefaultValue = formProperties.map(p => p.copy(defaultValue = None))
    val propertiesToConvertToFields: Set[CustomMetadata] = allPropertiesWithoutDefaultValue.toSet
    val customMetadataUtils = CustomMetadataUtils(allPropertiesWithoutDefaultValue)
    val fieldValuesByDataType: List[FormField] = customMetadataUtils.convertPropertiesToFormFields(propertiesToConvertToFields)

    propertiesToConvertToFields.foreach { property => checkMetadataToFieldConversion(property, fieldValuesByDataType) }
  }

  "convertPropertiesToFields" should "convert date property to field and it shouldn't allow future date when property name is foiExemptionAsserted" in {
    val propertiesToConvertToFields: CustomMetadata = formProperties
      .find(_.dataType == graphql.codegen.types.DataType.DateTime)
      .head
      .copy(name = "FoiExemptionAsserted", fullName = "FOI decision asserted".some, description = "Date of the Advisory Council Approval".some)
    val fieldValuesByDataType: List[FormField] = customMetadataUtils.convertPropertiesToFormFields(Set(propertiesToConvertToFields))

    val field = fieldValuesByDataType.head.asInstanceOf[DateField]
    verifyDate(field, isFutureDateAllowed = false)
    field.fieldDescription should equal(propertiesToConvertToFields.description.getOrElse(""))
    field.fieldName should equal(propertiesToConvertToFields.fullName.get)
    field.isRequired should equal(propertiesToConvertToFields.propertyGroup.contains("MandatoryMetadata"))
  }

  "convertPropertiesToFields" should "order the fields in the correct order" in {
    val propertiesToConvertToFields: Set[CustomMetadata] = formProperties.toSet
    val fieldValuesByDataType: List[FormField] = customMetadataUtils.convertPropertiesToFormFields(propertiesToConvertToFields)
    fieldValuesByDataType.size should equal(7)
    fieldValuesByDataType.map(_.fieldId) should equal(List("FoiExemptionAsserted", "ClosureStartDate", "ClosurePeriod", "Dropdown", "Radio", "TextArea", "TestProperty2"))
  }

  def verifyDate(field: DateField, isFutureDateAllowed: Boolean = true): Unit = {
    val isFutureDateAllowed = field.fieldName != "FOI decision asserted"
    field.day should equal(InputNameAndValue("Day", "", "DD"))
    field.month should equal(InputNameAndValue("Month", "", "MM"))
    field.year should equal(InputNameAndValue("Year", "", "YYYY"))
    field.isFutureDateAllowed should equal(isFutureDateAllowed)
  }

  def verifyBoolean(field: RadioButtonGroupField, defaultValue: Option[String]): Unit = {
    field.options should equal(Seq(InputNameAndValue("Yes", "yes"), InputNameAndValue("No", "no")))
    field.selectedOption should equal(defaultValue.map(v => if (v == "True") "yes" else "no").getOrElse("no"))
  }

  def verifyText(field: MultiSelectField, property: CustomMetadata): Unit = {
    property.propertyType match {
      case Defined =>
        field.options should equal(property.values.sortBy(_.uiOrdinal).map(v => InputNameAndValue(v.value, v.value)))
        field.selectedOption should equal(property.defaultValue.map(value => InputNameAndValue(value, value)))
      case Supplied =>
        field.options should equal(Seq())
        field.selectedOption should equal(property.defaultValue.map(value => InputNameAndValue(value, value)))
      case _ =>
        field.options should equal(Seq(InputNameAndValue(property.name, property.fullName.getOrElse(""))))
        field.selectedOption should equal(None)
    }
  }

  private def checkMetadataToFieldConversion(property: CustomMetadata, fieldValuesByDataType: List[FormField]): Unit = {
    val field = fieldValuesByDataType.find(_.fieldId == property.name).get

    property.dataType match {
      case Integer =>
        field.isInstanceOf[TextField] should be(true)
        field.asInstanceOf[TextField].nameAndValue should equal(InputNameAndValue("years", property.defaultValue.getOrElse("")))
      case DateTime =>
        field.isInstanceOf[DateField] should be(true)
        verifyDate(field.asInstanceOf[DateField])
      case Boolean =>
        field.isInstanceOf[RadioButtonGroupField] should be(true)
        val radioButtonGroupField = field.asInstanceOf[RadioButtonGroupField]
        verifyBoolean(radioButtonGroupField, property.defaultValue)
        radioButtonGroupField.dependencies.nonEmpty should be(true)
        for ((name, fields) <- radioButtonGroupField.dependencies) {
          val value = if (name == "yes") "True" else "False"
          fields.size should be(property.values.find(_.value == value).map(_.dependencies.size).getOrElse(0))
          fields.foreach(fi => checkMetadataToFieldConversion(formProperties.find(_.name == fi.fieldId).get, fields))
        }
      case Text =>
        property.propertyType match {
          case Defined =>
            field.isInstanceOf[MultiSelectField] should be(true)
            verifyText(field.asInstanceOf[MultiSelectField], property)
          case Supplied =>
            field.isInstanceOf[TextField] should be(true)
            field.asInstanceOf[TextField].nameAndValue should equal(InputNameAndValue(property.name, property.defaultValue.getOrElse("")))
          case unknownType => throw new IllegalArgumentException(s"Invalid type $unknownType")
        }

      case unknownType => throw new IllegalArgumentException(s"Invalid type $unknownType")
    }

    field.fieldDescription should equal(property.description.getOrElse(""))
    field.fieldName should equal(property.fullName.get)
    field.isRequired should equal(property.propertyGroup.contains("MandatoryMetadata"))
  }
}
