package testUtils

import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values.Dependencies
import graphql.codegen.types.DataType.{Boolean, DateTime, Integer, Text}
import graphql.codegen.types.PropertyType.{Defined, Supplied}
import services.DisplayProperty

class FormTestData() {

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
        Values("dropdownValue", List(Dependencies("TextArea")), 1),
        Values("dropdownValue2", Nil, 2)
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
        Values("True", List(Dependencies("DescriptionAlternate")), 1),
        Values("False", Nil, 2)
      ),
      None,
      allowExport = false
    )
    val dependencyProperty1 = CustomMetadata(
      "TextArea",
      Some("A TextArea "),
      Some("TextArea"),
      Supplied,
      Some("MandatoryMetadata"),
      Text,
      editable = true,
      multiValue = false,
      defaultValue = None,
      6,
      Nil,
      None,
      allowExport = false
    )
    val dependencyProperty2 = CustomMetadata(
      "DescriptionAlternate",
      Some("Alternative Description"),
      Some("Alternative Description"),
      Supplied,
      Some("OptionalClosure"),
      Text,
      editable = true,
      multiValue = false,
      defaultValue = None,
      7,
      Nil,
      None,
      allowExport = false
    )

    List(foiExemptionAsserted, closureStartDate, closurePeriod, dropdown, radio, dependencyProperty1, dependencyProperty2)
  }

  def dependencies(): List[String] = List("DescriptionAlternate")

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
        Some("guidance"),
        "label",
        multiValue = false,
        ordinal = 1,
        "FoiExemptionAsserted",
        "propertyType",
        "unitType",
        "summary",
        "alternativeName",
        required = true
      ),
      DisplayProperty(
        active = true,
        "date",
        DateTime,
        "description",
        "Date Display",
        editable = true,
        "group",
        Some("guidance"),
        "label",
        multiValue = false,
        ordinal = 2,
        "ClosureStartDate",
        "propertyType",
        "unitType",
        "summary",
        "alternativeName",
        required = true
      ),
      DisplayProperty(
        active = true,
        "small text",
        Integer,
        "description",
        "Small text Display",
        editable = true,
        "group",
        Some("years"),
        "label",
        multiValue = false,
        ordinal = 3,
        "ClosurePeriod",
        "propertyType",
        "unitType",
        "summary",
        "alternativeName",
        required = true
      ),
      DisplayProperty(
        active = true,
        "radial",
        Boolean,
        "description",
        "Radial Display",
        editable = true,
        "group",
        Some("guidance"),
        "yes|no",
        multiValue = false,
        ordinal = 4,
        "Radio",
        "propertyType",
        "unitType",
        "summary",
        "alternativeName",
        required = true
      ),
      DisplayProperty(
        active = true,
        "large text",
        Text,
        "description",
        "alternative description",
        editable = true,
        "group",
        Some("guidance"),
        "label",
        multiValue = false,
        ordinal = 4,
        "DescriptionAlternate",
        "propertyType",
        "unitType",
        "summary",
        "alternativeName",
        required = true
      ),
      DisplayProperty(
        active = true,
        "select",
        Text,
        "description",
        "Dropdown Display",
        editable = true,
        "group",
        Some("guidance"),
        "label",
        multiValue = true,
        5,
        "Dropdown",
        "propertyType",
        "unitType",
        "summary",
        "alternativeName",
        required = true
      )
    )
  }
}
