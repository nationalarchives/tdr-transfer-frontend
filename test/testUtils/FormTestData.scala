package testUtils

import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values.Dependencies
import graphql.codegen.types.DataType.{Boolean, DateTime, Integer, Text}
import graphql.codegen.types.PropertyType.{Defined, Supplied}
import services.DisplayProperty

class FormTestData() {

  // scalastyle:off method.length
  def setupCustomMetadata(): List[CustomMetadata] = {
    val foiExemptionAsserted = CustomMetadata(
      "FoiExemptionAsserted",
      Some("Date of the Advisory Council approval (or SIRO approval if appropriate)"),
      Some("FOI decision asserted"),
      Supplied,
      Some("MandatoryMetadata"),
      DateTime,
      editable = true,
      multiValue = false,
      defaultValue = None,
      1,
      values = Nil,
      None,
      allowExport = false
    )
    val closureStartDate = CustomMetadata(
      "ClosureStartDate",
      Some("This has been defaulted to the last date modified. If this is not correct, amend the field below."),
      Some("Closure start date"),
      Supplied,
      Some("OptionalClosure"),
      DateTime,
      editable = true,
      multiValue = false,
      defaultValue = None,
      2,
      Nil,
      None,
      allowExport = false
    )
    val closurePeriod = CustomMetadata(
      "ClosurePeriod",
      Some("Number of years the record is closed from the closure start date"),
      Some("Closure period"),
      Supplied,
      Some("MandatoryMetadata"),
      Integer,
      editable = true,
      multiValue = false,
      defaultValue = None,
      3,
      values = Nil,
      None,
      allowExport = false
    )
    val dropdown = CustomMetadata(
      "Dropdown",
      Some("A dropdown property"),
      Some("Dropdown"),
      Defined,
      Some("Dropdown property group"),
      Text,
      editable = true,
      multiValue = true,
      defaultValue = None,
      4,
      List(
        Values("dropdownValue", List(Dependencies("TestDropdownProperty")), 1),
        Values("dropdownValue2", List(Dependencies("TestDropdownProperty")), 2)
      ),
      None,
      allowExport = false
    )
    val radio = CustomMetadata(
      "Radio",
      Some("A radio "),
      Some("Radio property"),
      Supplied,
      Some("MandatoryMetadata"),
      Boolean,
      editable = true,
      multiValue = false,
      defaultValue = Some("false"),
      5,
      List(
        Values("True", List(Dependencies("TestProperty2")), 1),
        Values("False", Nil, 2)
      ),
      None,
      allowExport = false
    )
    val dependency = CustomMetadata(
      "TestProperty2",
      Some("A TestProperty2 "),
      Some("TestProperty2"),
      Supplied,
      Some("OptionalClosure"),
      Text,
      editable = true,
      multiValue = false,
      defaultValue = None,
      6,
      Nil,
      None,
      allowExport = false
    )

    List(foiExemptionAsserted, closureStartDate, closurePeriod, dropdown, radio, dependency)
  }
  // scalastyle:on method.length

  def dependencies(): List[String] = List("TestProperty2")

  def setupDisplayProperties(): List[DisplayProperty] = {
    List(
      DisplayProperty(
        active = true,
        "date",
        DateTime,
        "description",
        "Date Display",
        editable = true,
        "group",
        "guidance",
        "label",
        multiValue = false,
        ordinal = 1,
        "FoiExemptionAsserted",
        "propertyType",
        "unitType"
      ),
      DisplayProperty(
        active = true,
        "date",
        DateTime,
        "description",
        "Date Display",
        editable = true,
        "group",
        "guidance",
        "label",
        multiValue = false,
        ordinal = 2,
        "ClosureStartDate",
        "propertyType",
        "unitType"
      ),
      DisplayProperty(
        active = true,
        "small text",
        Text,
        "description",
        "Small text Display",
        editable = true,
        "group",
        "guidance",
        "label",
        multiValue = false,
        ordinal = 3,
        "ClosurePeriod",
        "propertyType",
        "unitType"
      ),
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
        ordinal = 4,
        "Radio",
        "propertyType",
        "unitType"
      ),
      DisplayProperty(
        active = true,
        "small text",
        Text,
        "description",
        "TestProperty2",
        editable = true,
        "group",
        "guidance",
        "label",
        multiValue = false,
        ordinal = 4,
        "TestProperty2",
        "propertyType",
        "unitType"
      ),
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
        5,
        "Dropdown",
        "propertyType",
        "unitType"
      )
    )
  }
}
