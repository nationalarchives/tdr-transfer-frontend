package testUtils

import java.util.UUID

object DefaultMockFormOptions {

  lazy val expectedPart1Options: List[MockInputOption] = List(
    MockInputOption(
      "publicRecord",
      "Public Records",
      value = "true",
      errorMessage = "All records must be confirmed as public before proceeding",
      fieldType = "inputCheckbox"
    ),
    MockInputOption(
      "crownCopyright",
      "Crown Copyright",
      value = "true",
      errorMessage = "All records must be confirmed Crown Copyright before proceeding",
      fieldType = "inputCheckbox"
    )
  )

  lazy val expectedPart2Options: List[MockInputOption] = List(
    MockInputOption(
      "droAppraisalSelection",
      "The appraisal and selection decision",
      value = "true",
      errorMessage = "Departmental Records Officer (DRO) must have signed off the appraisal and selection decision for records",
      fieldType = "inputCheckbox"
    ),
    MockInputOption(
      "droSensitivity",
      "The sensitivity review",
      value = "true",
      errorMessage = "Departmental Records Officer (DRO) must have signed off sensitivity review",
      fieldType = "inputCheckbox"
    )
  )

  val expectedConfirmTransferOptions: List[MockInputOption] = List(
    MockInputOption(
      "transferLegalCustody",
      "By proceeding with this transfer, I confirm that I am agreeing to transfer legal custody of these records to The National Archives.",
      value = "true",
      errorMessage = "Transferral of legal custody of all records must be confirmed before proceeding",
      fieldType = "inputCheckbox"
    )
  )

  def getExpectedSeriesDefaultOptions(seriesId: UUID): List[MockInputOption] = List(
    MockInputOption(
      name = "series",
      id = "series",
      placeholder = "Please choose...",
      fieldType = "inputDropdown",
      errorMessage = "error.required"
    ),
    MockInputOption(
      name = "series",
      id = "series",
      label = "MOCK1",
      value = s"$seriesId",
      fieldType = "inputDropdown",
      errorMessage = "error.required"
    )
  )

  case class MockInputOption(
      name: String,
      label: String = "",
      id: String = "",
      value: String = "",
      placeholder: String = "",
      fieldType: String = "",
      errorMessage: String = "",
      errorMessageDependency: String = "",
      rows: String = "",
      wrap: String = "",
      maxLength: String = ""
  ) // some fields (like month) can only display their error if another field (like day) has none
}
