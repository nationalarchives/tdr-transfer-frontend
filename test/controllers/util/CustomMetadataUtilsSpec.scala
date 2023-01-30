package controllers.util

import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
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
}
