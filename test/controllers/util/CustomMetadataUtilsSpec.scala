package controllers.util

import controllers.util.CustomMetadataUtils.FieldValues
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.types.DataType.{Boolean, DateTime, Integer, Text}
import graphql.codegen.types.PropertyType.{Defined, Supplied}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar

import scala.collection.immutable.ListSet

class CustomMetadataUtilsSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
  private val dataType = List(Text, DateTime)
  val allProperties: List[CustomMetadata] = (1 to 10).toList.map(
    number => {
      val numberOfValues = number % 5
      CustomMetadata(
        s"TestProperty$number",
        Some(s"It's the Test Property $number"),
        Some(s"Test Property $number"),
        if(numberOfValues > 2) Defined else Supplied,
        Some(s"Test Property Group $number"),
        if(numberOfValues == 1) {Integer} else if(numberOfValues == 2) {Boolean} else dataType(number % dataType.length),
        editable = true,
        multiValue = if(numberOfValues > 1) {true} else {false},
        Some(s"TestValue $number"),
        (1 to numberOfValues).toList.map(
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
    val namesOfPropertiesToGet = allProperties.map(_.name).to(ListSet)
    val listOfPropertiesRetrieved: Set[CustomMetadata] = customMetadataUtils.getCustomMetadataProperties(namesOfPropertiesToGet)

    val propertiesRetrievedEqualPropertiesRequested = listOfPropertiesRetrieved.forall(
      propertyRetrieved =>
        namesOfPropertiesToGet.contains(propertyRetrieved.name)
    )
    propertiesRetrievedEqualPropertiesRequested should equal(true)
  }

  "getCustomMetadataProperties" should "throw an 'NoSuchElementException' if any properties requested are not present" in {
    val namesOfPropertiesToGet = ListSet("TestProperty11", "TestProperty3")

    val thrownException: NoSuchElementException =
      the[NoSuchElementException] thrownBy customMetadataUtils.getCustomMetadataProperties(namesOfPropertiesToGet)

    thrownException.getMessage should equal("key not found: TestProperty11")
  }

  "getValuesOfProperties" should "return the values for a given property" in {
    val namesOfPropertiesAndTheirExpectedValues = Map(
      "TestProperty1" -> List("TestValue 1"),
      "TestProperty2" -> List("TestValue 1", "TestValue 2"),
      "TestProperty3" -> List("TestValue 1", "TestValue 2", "TestValue 3"),
      "TestProperty8" -> List("TestValue 1", "TestValue 2", "TestValue 3"),
    )

    val actualPropertiesAndTheirValues: Map[String, List[CustomMetadata.Values]] =
      customMetadataUtils.getValuesOfProperties(namesOfPropertiesAndTheirExpectedValues.keys.to(ListSet))

    namesOfPropertiesAndTheirExpectedValues.foreach {
      case (propertyName, expectedValues) =>
        actualPropertiesAndTheirValues(propertyName).map(_.value) should equal(expectedValues)
    }
  }

  "getValuesOfProperties" should "throw an 'NoSuchElementException' if any properties (from which to obtain values from) are not present" in {
    val namesOfPropertiesToGet = ListSet("TestProperty2", "TestProperty12", "TestProperty4")

    val thrownException: NoSuchElementException =
      the[NoSuchElementException] thrownBy customMetadataUtils.getCustomMetadataProperties(namesOfPropertiesToGet)

    thrownException.getMessage should equal("key not found: TestProperty12")
  }

  "convertPropertiesToFields" should "convert properties to fields for the form, if given correctly formatted properties" in {
    val propertiesToConvertToFields: ListSet[CustomMetadata] = allProperties.to(ListSet)
    val fieldValuesByDataType: Set[(FieldValues, String)] = customMetadataUtils.convertPropertiesToFields(propertiesToConvertToFields)
    val allFieldValues: Map[String, Iterable[FieldValues]] = fieldValuesByDataType.map(_._1).groupBy(_.fieldId)

    propertiesToConvertToFields.foreach{
      property =>
        val field = allFieldValues(property.name.toLowerCase()).head
        field.fieldId should equal(property.name.toLowerCase())
        if(property.dataType == DateTime) field.fieldOptions.length should equal(3) else field.fieldOptions.length should equal(property.values.length)
        field.fieldHint should equal(property.description.getOrElse(""))
        field.fieldLabel should equal(property.fullName.get)
        field.fieldRequired should equal(if(property.propertyGroup.getOrElse("") == "MandatoryMetadata") true else false)
    }
  }
}
