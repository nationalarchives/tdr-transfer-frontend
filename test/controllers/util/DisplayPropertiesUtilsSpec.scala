package controllers.util

import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values
import graphql.codegen.types.DataType.{DateTime, Text}
import graphql.codegen.types.PropertyType.{Defined, Supplied}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar
import services.DisplayProperty
import testUtils.FormTestData

class DisplayPropertiesUtilsSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
  private val customMetadata = new FormTestData().setupCustomMetadata()
  private val displayProperties = new FormTestData().setupDisplayProperties()
  private val displayPropertiesUtils = new DisplayPropertiesUtils(displayProperties, customMetadata)

  "convertPropertiesToFormFields" should "return the list of properties requested in ordinal order" in {
    val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields

    fields.size should equal(3)
    fields.head.fieldId should equal("Dropdown1")
    fields.tail.head.fieldId should equal("Dropdown2")
    fields.last.fieldId should equal("Dropdown3")
  }

  "convertPropertiesToFormFields" should "generate 'dropdown' field for componentType value 'select' where the property is not mult-value" in {
    val customMetadata = CustomMetadata(
      "Dropdown1",
      None,
      None,
      Defined,
      None,
      Text,
      editable = true,
      multiValue = false,
      defaultValue = Some("dropdownValue1"),
      4,
      List(Values("dropdownValue3", List(), 3), Values("dropdownValue1", List(), 1), Values("dropdownValue2", List(), 2)),
      None,
      allowExport = false
    )
    val displayProperty = DisplayProperty(true, "select", Text, "description", "Dropdown Display", true, "group", "guidance", "label", false, 3, "Dropdown1", "propertyType", "")

    val displayPropertiesUtils = new DisplayPropertiesUtils(List(displayProperty), List(customMetadata))
    val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields

    val dropdownField: DropdownField = fields.head.asInstanceOf[DropdownField]
    dropdownField.fieldId should equal("Dropdown1")
    dropdownField.multiValue should equal(false)
    dropdownField.fieldName should equal("Dropdown Display")
    dropdownField.fieldDescription should equal("description")
    dropdownField.isRequired should equal(false)
    dropdownField.fieldErrors should equal(Nil)

    dropdownField.selectedOption.get should equal(InputNameAndValue("dropdownValue1", "dropdownValue1"))
    dropdownField.options should equal(customMetadata.values.sortBy(_.uiOrdinal).map(v => InputNameAndValue(v.value, v.value)))
  }

  "convertPropertiesToFormFields" should "generate 'multi-select' field for componentType value 'select' where display property is multi value" in {
    val customMetadata = CustomMetadata(
      "Dropdown1",
      None,
      None,
      Defined,
      None,
      Text,
      editable = true,
      multiValue = true,
      defaultValue = Some("dropdownValue1"),
      4,
      List(Values("dropdownValue3", List(), 3), Values("dropdownValue1", List(), 1), Values("dropdownValue2", List(), 2)),
      None,
      allowExport = false
    )
    val displayProperty = DisplayProperty(true, "select", Text, "description", "Dropdown Display", true, "group", "guidance", "label", true, 3, "Dropdown1", "propertyType", "")

    val displayPropertiesUtils = new DisplayPropertiesUtils(List(displayProperty), List(customMetadata))
    val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields

    val multiSelectField: MultiSelectField = fields.head.asInstanceOf[MultiSelectField]
    multiSelectField.fieldId should equal("Dropdown1")
    multiSelectField.multiValue should equal(true)
    multiSelectField.fieldName should equal("Dropdown Display")
    multiSelectField.fieldDescription should equal("description")
    multiSelectField.isRequired should equal(false)
    multiSelectField.fieldErrors should equal(Nil)
    multiSelectField.selectedOption.get should equal(List(InputNameAndValue("dropdownValue1", "dropdownValue1")))
    multiSelectField.options should equal(customMetadata.values.sortBy(_.uiOrdinal).map(v => InputNameAndValue(v.value, v.value)))
  }

  "convertPropertiesToFormFields" should "generate 'text area' field for componentType value 'large text'" in {
    val customMetadata = CustomMetadata(
      "TextField",
      None,
      None,
      Defined,
      None,
      Text,
      editable = true,
      multiValue = false,
      defaultValue = Some("defaultValue"),
      4,
      List(),
      None,
      allowExport = false
    )
    val displayProperty =
      DisplayProperty(true, "large text", Text, "description", "TextField Display", true, "group", "guidance", "label", false, 3, "TextField", "propertyType", "")

    val displayPropertiesUtils = new DisplayPropertiesUtils(List(displayProperty), List(customMetadata))
    val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields

    val textAreaField: TextAreaField = fields.head.asInstanceOf[TextAreaField]
    textAreaField.fieldId should equal("TextField")
    textAreaField.fieldName should equal("TextField Display")
    textAreaField.fieldDescription should equal("description")
    textAreaField.fieldErrors should equal(Nil)
    textAreaField.isRequired should equal(false)
    textAreaField.multiValue should equal(false)
    textAreaField.rows should equal("5")
    textAreaField.wrap should equal("hard")
    textAreaField.characterLimit should equal(8000)
    textAreaField.nameAndValue.name should equal("TextField")
    textAreaField.nameAndValue.value should equal("defaultValue")
  }

  "convertPropertiesToFormFields" should "throw an error for an unsupported component type" in {
    val displayProperty =
      DisplayProperty(true, "unsupportedComponentType", Text, "description", "Dropdown Display", true, "group", "guidance", "label", false, 3, "Dropdown1", "propertyType", "")
    val displayPropertiesUtils = new DisplayPropertiesUtils(List(displayProperty), List())

    val thrownException = intercept[Exception] {
      displayPropertiesUtils.convertPropertiesToFormFields
    }

    thrownException.getMessage should equal("unsupportedComponentType is not a supported component type")
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

    displayPropertiesUtils.CustomMetadataHelper(Some(noDefaultValue)).defaultInput should equal(None)
    displayPropertiesUtils.CustomMetadataHelper(None).defaultInput should equal(None)
  }

  "CustomMetadataHelper" should "return the correct 'defined inputs' for custom metadata" in {
    val noValues = createCustomMetadata()
    val withValues = createCustomMetadata(values =
      List(
        Values("value3", List(), 3),
        Values("value1", List(), 1),
        Values("value2", List(), 2)
      )
    )

    val values = displayPropertiesUtils.CustomMetadataHelper(Some(withValues)).definedInputs
    values.size should equal(3)
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

    displayPropertiesUtils.CustomMetadataHelper(Some(mandatoryClosure)).requiredField should equal(true)
    displayPropertiesUtils.CustomMetadataHelper(Some(mandatoryMetadata)).requiredField should equal(true)
    displayPropertiesUtils.CustomMetadataHelper(Some(other)).requiredField should equal(false)
    displayPropertiesUtils.CustomMetadataHelper(None).requiredField should equal(false)
  }

  def createCustomMetadata(propertyGroup: String = "defaultGroup", defaultValue: Option[String] = None, values: List[Values] = Nil): CustomMetadata = {
    CustomMetadata("Property", None, None, Supplied, Some(propertyGroup), DateTime, editable = true, multiValue = false, defaultValue, 1, values, None, allowExport = false)
  }
}
