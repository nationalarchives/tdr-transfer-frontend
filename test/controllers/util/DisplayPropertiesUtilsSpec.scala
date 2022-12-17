package controllers.util

import cats.implicits.catsSyntaxOptionId
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values.Dependencies
import graphql.codegen.types.DataType.{Boolean, DateTime, Integer, Text}
import graphql.codegen.types.PropertyType.{Defined, Supplied}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar
import testUtils.FormTestData

class DisplayPropertiesUtilsSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
  private val customMetadata = new FormTestData().setupCustomMetadata()
  private val displayProperties = new FormTestData().setupDisplayProperties()
  private val displayPropertiesUtils = new DisplayPropertiesUtils(displayProperties, customMetadata)

  "convertPropertiesToFormFields" should "return the list of properties requested in ordinal order" in {
    val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields

    fields.size shouldBe 3
    fields.head.fieldId should equal("Dropdown1")
    fields.tail.head.fieldId should equal("Dropdown2")
    fields.last.fieldId should equal("Dropdown3")
  }

  "convertPropertiesToFormFields" should "generate 'dropdown' field for componentType value 'dropdown'" in {

  }

  "convertPropertiesToFormFields" should "generate 'text field' field for componentType value 'large text'" in {

  }

  "convertPropertiesToFormFields" should "throw an error for an unsupported component type" in {

  }

  "CustomMetadataHelper" should "return the correct 'default value' for custom metadata" in {
    val defaultValue = createCustomMetadata(defaultValue = Some("someValue"))
    val noDefaultValue = createCustomMetadata()

    displayPropertiesUtils.CustomMetadataHelper(Some(defaultValue)).defaultValue should equal("someValue")
    displayPropertiesUtils.CustomMetadataHelper(Some(noDefaultValue)).defaultValue should equal("")
    displayPropertiesUtils.CustomMetadataHelper(None).defaultValue should equal("")
  }

  "CustomMetadataHelper" should "return the correct 'default input' for custom metadata" in {
    val defaultValue = createCustomMetadata(defaultValue = Some("someValue"))
    val noDefaultValue = createCustomMetadata()

    val someDefaultInput = displayPropertiesUtils.CustomMetadataHelper(Some(defaultValue)).defaultInput.get
    someDefaultInput.name should equal("someValue")
    someDefaultInput.value should equal("someValue")

    displayPropertiesUtils.CustomMetadataHelper(Some(noDefaultValue)).defaultInput shouldBe None
    displayPropertiesUtils.CustomMetadataHelper(None).defaultInput shouldBe None
  }

  "CustomMetadataHelper" should "return the correct 'defined inputs' for custom metadata" in {
    val noValues = createCustomMetadata()
    val withValues = createCustomMetadata(values = List(
      Values("value3", List(), 3),
      Values("value1", List(), 1),
      Values("value2", List(), 2)
    ))
    
    val values = displayPropertiesUtils.CustomMetadataHelper(Some(withValues)).definedInputs
    values.size shouldBe 3
    values.head.name should equal("value1")
    values.head.value should equal("value1")
    values.tail.head.name should equal("value2")
    values.tail.head.value should equal("value2")
    values.last.name should equal("value3")
    values.last.value should equal("value3")

    displayPropertiesUtils.CustomMetadataHelper(Some(noValues)).definedInputs should equal(List())
    displayPropertiesUtils.CustomMetadataHelper(None).definedInputs should equal(List())
  }

  "CustomMetadataHelper" should "return the correct 'required' value for custom metadata" in {
    val mandatoryMetadata = createCustomMetadata("MandatoryMetadata")
    val mandatoryClosure = createCustomMetadata("MandatoryClosure")
    val other = createCustomMetadata("OtherPropertyGroup")

    displayPropertiesUtils.CustomMetadataHelper(Some(mandatoryClosure)).requiredField shouldBe true
    displayPropertiesUtils.CustomMetadataHelper(Some(mandatoryMetadata)).requiredField shouldBe true
    displayPropertiesUtils.CustomMetadataHelper(Some(other)).requiredField shouldBe false
    displayPropertiesUtils.CustomMetadataHelper(None).requiredField shouldBe false
  }

  def createCustomMetadata(propertyGroup: String = "defaultGroup", defaultValue: Option[String] = None, values: List[Values] = Nil): CustomMetadata = {
    CustomMetadata("Property", None, None, Supplied, Some(propertyGroup),
      DateTime, editable = true, multiValue = false, defaultValue, 1, values, None, allowExport = false)
  }
}
