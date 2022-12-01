package testUtils

import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values
import graphql.codegen.GetCustomMetadata.customMetadata.CustomMetadata.Values.Dependencies
import graphql.codegen.types.DataType.{Boolean, DateTime, Integer, Text}
import graphql.codegen.types.PropertyType.{Defined, Supplied}

class FormTestData() {

  // scalastyle:off method.length
  def setupCustomMetadatas(): List[CustomMetadata] = {
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
      multiValue = false,
      defaultValue = None,
      4,
      List(
        Values("dropdownValue", List(Dependencies("TestDropdownProperty")), 3)
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
      defaultValue = None,
      5,
      List(
        Values("yes", List(Dependencies("TestProperty2")), 1),
        Values("no", Nil, 2)
      ),
      None,
      allowExport = false
    )

    List(foiExemptionAsserted, closureStartDate, closurePeriod, dropdown, radio)
  }
  // scalastyle:on method.length
}
