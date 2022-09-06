package controllers.util

import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType.{Boolean, DateTime, Integer, Text}
import graphql.codegen.types.PropertyType.{Defined, Supplied}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar

class CustomMetadataUtilsSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
  private val dataType = List(Text, DateTime)
  val allProperties: List[CustomMetadata] = (1 to 10).toList.map(
    number => {
      val numberOfValues = number % 5
      CustomMetadata(
        name = s"TestProperty$number",
        description = Some(s"It's the Test Property $number"),
        fullName = Some(s"Test Property $number"),
        propertyType = if (numberOfValues > 2) Defined else Supplied,
        propertyGroup = Some(s"Test Property Group $number"),
        dataType = if (numberOfValues == 1) {
          Integer
        } else if (numberOfValues == 2) {
          Boolean
        } else {
          dataType(number % dataType.length)
        },
        editable = true,
        multiValue = numberOfValues > 1,
        defaultValue = Some(s"TestValue $number"),
        ordinal = number,
        values = (1 to numberOfValues).toList.map(
          valueNumber =>
            CustomMetadata.Values(
              s"TestValue $valueNumber",
              (1 to number % 6).toList.map(
                depNumber => {
                  val propertyNumber = depNumber * 2 % 11
                  CustomMetadata.Values.Dependencies(s"TestProperty$propertyNumber")
                }
              )
            )
        )
      )
    }
  )

  private val customMetadataUtils = CustomMetadataUtils(allProperties)


  "getCustomMetadataProperties" should "return the list of properties requested" in {
    val namesOfPropertiesToGet = allProperties.map(_.name).toSet
    val listOfPropertiesRetrieved: Set[CustomMetadata] = customMetadataUtils.getCustomMetadataProperties(namesOfPropertiesToGet)

    val propertiesRetrievedEqualPropertiesRequested = listOfPropertiesRetrieved.forall(
      propertyRetrieved =>
        namesOfPropertiesToGet.contains(propertyRetrieved.name)
    )
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
      "TestProperty1" -> List("TestValue 1"),
      "TestProperty2" -> List("TestValue 1", "TestValue 2"),
      "TestProperty3" -> List("TestValue 1", "TestValue 2", "TestValue 3"),
      "TestProperty8" -> List("TestValue 1", "TestValue 2", "TestValue 3")
    )

    val actualPropertiesAndTheirValues: Map[String, List[CustomMetadata.Values]] =
      customMetadataUtils.getValuesOfProperties(namesOfPropertiesAndTheirExpectedValues.keys.toSet)

    namesOfPropertiesAndTheirExpectedValues.foreach {
      case (propertyName, expectedValues) =>
        actualPropertiesAndTheirValues(propertyName).map(_.value) should equal(expectedValues)
    }
  }

  "getValuesOfProperties" should "throw an 'NoSuchElementException' if any properties (from which to obtain values from) are not present" in {
    val namesOfPropertiesToGet = Set("TestProperty2", "TestProperty12", "TestProperty4")

    val thrownException: NoSuchElementException =
      the[NoSuchElementException] thrownBy customMetadataUtils.getCustomMetadataProperties(namesOfPropertiesToGet)

    thrownException.getMessage should equal("key not found: TestProperty12")
  }

  "convertPropertiesToFields" should "convert properties to fields for the form, if given correctly formatted properties" in {
    val propertiesToConvertToFields: Set[CustomMetadata] = allProperties.toSet
    val fieldValuesByDataType: List[FormField] = customMetadataUtils.convertPropertiesToFormFields(propertiesToConvertToFields)

    propertiesToConvertToFields.foreach {
      property =>
        val field = fieldValuesByDataType.find(_.fieldId == property.name).get
        property.dataType match {
          case Integer => field.isInstanceOf[TextField] should be(true)
            field.asInstanceOf[TextField].nameAndValue should equal(InputNameAndValue("years", "0", "0"))
          case DateTime => field.isInstanceOf[DateField] should be(true)
            verifyDate(field.asInstanceOf[DateField])
          case Boolean => field.isInstanceOf[RadioButtonGroupField] should be(true)
            verifyBoolean(field.asInstanceOf[RadioButtonGroupField], property.defaultValue)
          case Text => field.isInstanceOf[DropdownField] should be(true)
            verifyText(field.asInstanceOf[DropdownField], property)
        }
        field.fieldDescription should equal(property.description.getOrElse(""))
        field.fieldName should equal(property.fullName.get)
        field.isRequired should equal(property.propertyGroup.contains("MandatoryMetadata"))
    }
  }

  "convertPropertiesToFields" should "order the fields in the correct order" in {
    val propertiesToConvertToFields: Set[CustomMetadata] = allProperties.toSet
    val fieldValuesByDataType: List[FormField] = customMetadataUtils.convertPropertiesToFormFields(propertiesToConvertToFields)
    fieldValuesByDataType.size should equal(10)
    (1 to 10).toList.foreach(number =>
      fieldValuesByDataType(number - 1).fieldId should equal(s"TestProperty$number")
    )
  }

  def verifyDate(field: DateField): Unit = {
    field.day should equal(InputNameAndValue("Day", "", "DD"))
    field.month should equal(InputNameAndValue("Month", "", "MM"))
    field.year should equal(InputNameAndValue("Year", "", "YYYY"))
  }

  def verifyBoolean(field: RadioButtonGroupField, defaultValue: Option[String]): Unit = {
    field.options should equal(Seq(InputNameAndValue("Yes", "yes"), InputNameAndValue("No", "no")))
    field.selectedOption should equal(defaultValue.map(v => if (v == "True") "yes" else "no").getOrElse("no"))
  }

  def verifyText(field: DropdownField, property: CustomMetadata): Unit = {
    property.propertyType match {
      case Defined =>
        field.options should equal(property.values.map(v => InputNameAndValue(v.value, v.value)))
        field.selectedOption should equal(property.defaultValue.map(value => InputNameAndValue(value, value)))
      case Supplied =>
        field.options should equal(Seq())
        field.selectedOption should equal(property.defaultValue.map(value => InputNameAndValue(value, value)))
      case _ =>
        field.options should equal(Seq(InputNameAndValue(property.name, property.fullName.getOrElse(""))))
        field.selectedOption should equal(None)
    }
  }
}
