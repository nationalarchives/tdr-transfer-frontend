package controllers.util

import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values
import graphql.codegen.types.DataType.{Boolean, DateTime, Text}
import graphql.codegen.types.PropertyType.{Defined, Supplied}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.TableFor3
import org.scalatest.prop.Tables.Table
import org.scalatestplus.mockito.MockitoSugar
import services.DisplayProperty
import testUtils.FormTestData

class DisplayPropertiesUtilsSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
  private val customMetadata = new FormTestData().setupCustomMetadata()
  private val displayProperties = new FormTestData().setupDisplayProperties()
  private val displayPropertiesUtils = new DisplayPropertiesUtils(displayProperties, customMetadata)

  "convertPropertiesToFormFields" should "return the list of properties requested in ordinal order" in {
    val fields: Seq[String] = displayPropertiesUtils.convertPropertiesToFormFields(displayProperties).map(_.fieldId)

    fields.size should equal(6)
    fields should equal(List("FoiExemptionAsserted", "ClosureStartDate", "ClosurePeriod", "Radio", "DescriptionAlternate", "Dropdown"))
  }

  "convertPropertiesToFormFields" should "generate 'dropdown' field for componentType value 'select' where the property is not multi-value" in {
    val customMetadata = CustomMetadata(
      "Dropdown",
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
    val displayProperty =
      DisplayProperty(
        active = true,
        "select",
        Text,
        "description",
        "Dropdown Display",
        editable = true,
        "group",
        "guidance",
        "label",
        multiValue = false,
        3,
        "Dropdown",
        "propertyType",
        "",
        "",
        "alternativeName",
        required = false
      )

    val displayPropertiesUtils = new DisplayPropertiesUtils(List(displayProperty), List(customMetadata))
    val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields(List(displayProperty))

    val dropdownField: DropdownField = fields.head.asInstanceOf[DropdownField]
    dropdownField.fieldId should equal("Dropdown")
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
    val displayProperty =
      DisplayProperty(
        active = true,
        "select",
        Text,
        "description",
        "Dropdown Display",
        editable = true,
        "group",
        "guidance",
        "label",
        multiValue = true,
        3,
        "Dropdown1",
        "propertyType",
        "",
        "",
        "alternativeName",
        required = false
      )

    val displayPropertiesUtils = new DisplayPropertiesUtils(List(displayProperty), List(customMetadata))
    val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields(List(displayProperty))

    val multiSelectField: MultiSelectField = fields.head.asInstanceOf[MultiSelectField]
    multiSelectField.fieldId should equal("Dropdown1")
    multiSelectField.multiValue should equal(true)
    multiSelectField.fieldName should equal("Dropdown Display")
    multiSelectField.fieldDescription should equal("description")
    multiSelectField.fieldGuidance should equal("guidance")
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
      DisplayProperty(
        active = true,
        "large text",
        Text,
        "description",
        "TextField Display",
        editable = true,
        "group",
        "guidance",
        "label",
        multiValue = false,
        3,
        "TextField",
        "propertyType",
        "",
        "",
        "alternativeName",
        required = false
      )

    val displayPropertiesUtils = new DisplayPropertiesUtils(List(displayProperty), List(customMetadata))
    val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields(List(displayProperty))

    val textAreaField: TextAreaField = fields.head.asInstanceOf[TextAreaField]
    textAreaField.fieldId should equal("TextField")
    textAreaField.fieldName should equal("TextField Display")
    textAreaField.fieldDescription should equal("description")
    textAreaField.fieldErrors should equal(Nil)
    textAreaField.isRequired should equal(false)
    textAreaField.multiValue should equal(false)
    textAreaField.rows should equal("5")
    textAreaField.wrap should equal("soft")
    textAreaField.characterLimit should equal(8000)
    textAreaField.nameAndValue.name should equal("TextField")
    textAreaField.nameAndValue.value should equal("defaultValue")
  }

  "convertPropertiesToFormFields" should "generate 'radio' field for componentType value 'radial'" in {
    val displayProperty =
      DisplayProperty(
        active = true,
        "radial",
        Boolean,
        "description",
        "Radial Display",
        editable = true,
        "group",
        "guidance",
        "yes|no",
        multiValue = false,
        3,
        "Radio",
        "propertyType",
        "",
        "",
        "alternativeName",
        required = true
      )

    val displayPropertiesUtils = new DisplayPropertiesUtils(List(displayProperty), customMetadata)
    val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields(List(displayProperty))

    val radioButtonGroupField: RadioButtonGroupField = fields.head.asInstanceOf[RadioButtonGroupField]
    radioButtonGroupField.fieldId should equal("Radio")
    radioButtonGroupField.fieldName should equal("Radial Display")
    radioButtonGroupField.fieldDescription should equal("description")
    radioButtonGroupField.fieldErrors should equal(Nil)
    radioButtonGroupField.isRequired should equal(true)
    radioButtonGroupField.multiValue should equal(false)
    radioButtonGroupField.selectedOption should equal("no")
    radioButtonGroupField.options should contain theSameElementsAs List(InputNameAndValue("yes", "yes"), InputNameAndValue("no", "no"))
    radioButtonGroupField.dependencies.size should equal(2)
  }

  "convertPropertiesToFormFields" should "generate 'date' field for componentType value 'date'" in {
    val displayProperty =
      DisplayProperty(
        active = true,
        "date",
        Boolean,
        "description",
        "Date Display",
        editable = true,
        "group",
        "guidance",
        "label",
        multiValue = false,
        3,
        "ClosureStartDate",
        "propertyType",
        "",
        "",
        "alternativeName",
        required = false
      )

    val displayPropertiesUtils = new DisplayPropertiesUtils(List(displayProperty), customMetadata)
    val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields(List(displayProperty))

    val dateField: DateField = fields.head.asInstanceOf[DateField]
    dateField.fieldId should equal("ClosureStartDate")
    dateField.fieldName should equal("Date Display")
    dateField.fieldDescription should equal("description")
    dateField.fieldErrors should equal(Nil)
    dateField.isRequired should equal(false)
    dateField.multiValue should equal(false)
    dateField.day should equal(InputNameAndValue("Day", "", "DD"))
    dateField.month should equal(InputNameAndValue("Month", "", "MM"))
    dateField.year should equal(InputNameAndValue("Year", "", "YYYY"))
  }

  "convertPropertiesToFormFields" should "generate 'text' field for componentType value 'small text'" in {
    val displayProperty =
      DisplayProperty(
        active = true,
        "small text",
        Text,
        "description",
        "Text Display",
        editable = true,
        "group",
        "guidance",
        "label",
        multiValue = false,
        3,
        "TestProperty2",
        "propertyType",
        "",
        "",
        "alternativeName",
        required = true
      )

    val displayPropertiesUtils = new DisplayPropertiesUtils(List(displayProperty), customMetadata)
    val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields(List(displayProperty))

    val textField: TextField = fields.head.asInstanceOf[TextField]
    textField.fieldId should equal("TestProperty2")
    textField.fieldName should equal("Text Display")
    textField.fieldDescription should equal("description")
    textField.fieldErrors should equal(Nil)
    textField.inputMode should equal("text")
    textField.isRequired should equal(false)
    textField.multiValue should equal(false)
  }

  "convertPropertiesToFormFields" should "set field 'required' based on custom metadata group and if display property is required" in {
    val requiredGroup = "MandatoryClosure"
    val optionalGroup = "OptionalClosure"

    val optionsAndExpectedOutcome: TableFor3[String, Boolean, Boolean] = Table(
      ("custom metadata group", "display properties required", "expected field required"),
      (requiredGroup, true, true),
      (requiredGroup, false, true),
      (optionalGroup, true, true),
      (optionalGroup, false, false)
    )

    optionsAndExpectedOutcome.foreach(options => {
      val group = options._1
      val displayPropertyRequired = options._2
      val expected = options._3
      val customMetadata = createCustomMetadata(propertyGroup = group)
      val displayProperty =
        DisplayProperty(
          active = true,
          "large text",
          Text,
          "description",
          "TextField Display",
          editable = true,
          "group",
          "guidance",
          "label",
          multiValue = false,
          3,
          "Property",
          "propertyType",
          "",
          "",
          "alternativeName",
          required = displayPropertyRequired
        )
      val displayPropertiesUtils = new DisplayPropertiesUtils(List(displayProperty), List(customMetadata))
      val fields: Seq[FormField] = displayPropertiesUtils.convertPropertiesToFormFields(List(displayProperty))

      val field: TextAreaField = fields.head.asInstanceOf[TextAreaField]
      field.isRequired should equal(expected)
    })
  }

  "convertPropertiesToFormFields" should "throw an error for an unsupported component type" in {
    val displayProperty =
      DisplayProperty(
        active = true,
        "unsupportedComponentType",
        Text,
        "description",
        "Dropdown Display",
        editable = true,
        "group",
        "guidance",
        "label",
        multiValue = false,
        3,
        "Dropdown1",
        "propertyType",
        "",
        "",
        "alternativeName",
        required = true
      )
    val displayPropertiesUtils = new DisplayPropertiesUtils(List(displayProperty), List())

    val thrownException = intercept[Exception] {
      displayPropertiesUtils.convertPropertiesToFormFields(List(displayProperty))
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
