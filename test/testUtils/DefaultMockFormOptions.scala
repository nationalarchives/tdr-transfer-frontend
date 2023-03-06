package testUtils

import java.util.UUID

object DefaultMockFormOptions {

  lazy val expectedPart1Options: List[MockInputOption] = List(
    MockInputOption(
      "publicRecord",
      "I confirm that the records are Public Records.",
      value = "true",
      errorMessage = "All records must be confirmed as public before proceeding",
      fieldType = "inputCheckbox"
    ),
    MockInputOption(
      "crownCopyright",
      "I confirm that the records are all Crown Copyright.",
      value = "true",
      errorMessage = "All records must be confirmed Crown Copyright before proceeding",
      fieldType = "inputCheckbox"
    )
  )

  lazy val expectedPart2Options: List[MockInputOption] = List(
    MockInputOption(
      "droAppraisalSelection",
      "I confirm that the Departmental Records Officer (DRO) has signed off on the appraisal and selection",
      value = "true",
      errorMessage = "Departmental Records Officer (DRO) must have signed off the appraisal and selection decision for records",
      fieldType = "inputCheckbox"
    ),
    MockInputOption(
      "droSensitivity",
      "I confirm that the Departmental Records Officer (DRO) has signed off on the sensitivity review.",
      value = "true",
      errorMessage = "Departmental Records Officer (DRO) must have signed off sensitivity review",
      fieldType = "inputCheckbox"
    )
  )

  val expectedDescriptiveDefaultOptions: List[MockInputOption] = List(
    MockInputOption(
      name = "inputdate-end_date-day",
      label = "Day",
      id = "date-input-end_date-day",
      placeholder = "dd",
      fieldType = "inputDate",
      errorMessage = s"The date of the record must contain a day"
    ),
    MockInputOption(
      name = "inputdate-end_date-month",
      label = "Month",
      id = "date-input-end_date-month",
      placeholder = "mm",
      fieldType = "inputDate",
      errorMessage = s"The date of the record must contain a month",
      errorMessageDependency = "inputdate-end_date-day"
    ),
    MockInputOption(
      name = "inputdate-end_date-year",
      label = "Year",
      id = "date-input-end_date-year",
      placeholder = "yyyy",
      fieldType = "inputDate",
      errorMessage = s"The date of the record must contain a year",
      errorMessageDependency = "inputdate-end_date-month"
    ),
    MockInputOption(
      name = "inputtextarea-description",
      id = "inputtextarea-description",
      fieldType = "inputTextArea",
      rows = "5",
      wrap = "soft",
      maxLength = "8000"
    ),
    MockInputOption(
      name = "inputmultiselect-Language",
      id = "inputmultiselect-Language-0",
      fieldType = "inputmultiselect",
      label = "English",
      value = "English"
    ),
    MockInputOption(
      name = "inputmultiselect-Language",
      id = "inputmultiselect-Language-1",
      fieldType = "inputmultiselect",
      label = "Welsh",
      value = "Welsh"
    )
  )

  val expectedClosureDefaultOptions: List[MockInputOption] = List(
    MockInputOption(
      name = "inputdate-ClosureStartDate-day",
      label = "Day",
      id = "date-input-ClosureStartDate-day",
      placeholder = "dd",
      fieldType = "inputDate",
      errorMessage = s"The closure start date must contain a day"
    ),
    MockInputOption(
      name = "inputdate-ClosureStartDate-month",
      label = "Month",
      id = "date-input-ClosureStartDate-month",
      placeholder = "mm",
      fieldType = "inputDate",
      errorMessage = s"The closure start date must contain a month",
      errorMessageDependency = "inputdate-ClosureStartDate-day"
    ),
    MockInputOption(
      name = "inputdate-ClosureStartDate-year",
      label = "Year",
      id = "date-input-ClosureStartDate-year",
      placeholder = "yyyy",
      fieldType = "inputDate",
      errorMessage = s"There was no number entered for the Year.",
      errorMessageDependency = "inputdate-ClosureStartDate-month"
    ),
    MockInputOption(
      name = "inputdate-FoiExemptionAsserted-day",
      label = "Day",
      id = "date-input-FoiExemptionAsserted-day",
      placeholder = "dd",
      fieldType = "inputDate",
      errorMessage = s"The foi decision asserted must contain a day"
    ),
    MockInputOption(
      name = "inputdate-FoiExemptionAsserted-month",
      label = "Month",
      id = "date-input-FoiExemptionAsserted-month",
      placeholder = "mm",
      fieldType = "inputDate",
      errorMessage = s"The foi decision asserted must contain a month",
      errorMessageDependency = "inputdate-FoiExemptionAsserted-day"
    ),
    MockInputOption(
      name = "inputdate-FoiExemptionAsserted-year",
      label = "Year",
      id = "date-input-FoiExemptionAsserted-year",
      placeholder = "yyyy",
      fieldType = "inputDate",
      errorMessage = s"The foi decision asserted must contain a year",
      errorMessageDependency = "inputdate-FoiExemptionAsserted-month"
    ),
    MockInputOption(
      name = "inputnumeric-ClosurePeriod-years",
      label = "years",
      id = "years",
      fieldType = "inputNumeric",
      errorMessage = s"Enter the number of years the record is closed from the closure start date"
    ),
    MockInputOption(
      name = "inputmultiselect-FoiExemptionCode",
      id = "inputmultiselect-FoiExemptionCode-0",
      label = "mock code1",
      value = "mock code1",
      fieldType = "inputmultiselect",
      errorMessage = "Search for and select at least one FOI exemption code(s)"
    ),
    MockInputOption(
      name = "inputmultiselect-FoiExemptionCode",
      id = "inputmultiselect-FoiExemptionCode-1",
      label = "mock code2",
      value = "mock code2",
      fieldType = "inputmultiselect",
      errorMessage = "Search for and select at least one FOI exemption code(s)"
    ),
    MockInputOption(
      name = "inputradio-TitleClosed",
      label = "Yes",
      id = "inputradio-TitleClosed-Yes",
      value = "yes",
      fieldType = "inputRadio",
      errorMessage = s"There was no value selected for Is the title closed?."
    ),
    MockInputOption(
      name = "inputradio-TitleClosed",
      label = "No",
      id = "inputradio-TitleClosed-No, this title can be made public",
      value = "no",
      errorMessage = s"There was no value selected for Is the title closed?.",
      fieldType = "inputRadio"
    ),
    MockInputOption(
      name = "inputradio-DescriptionClosed",
      label = "Yes",
      id = "inputradio-DescriptionClosed-Yes",
      value = "yes",
      fieldType = "inputRadio",
      errorMessage = s"There was no value selected for Is the description closed?."
    ),
    MockInputOption(
      name = "inputradio-DescriptionClosed",
      label = "No",
      id = "inputradio-DescriptionClosed-No",
      value = "no",
      errorMessage = s"There was no value selected for Is the description closed?.",
      fieldType = "inputRadio"
    )
  )

  val expectedClosureDependencyDefaultOptions: List[MockInputOption] = List(
    MockInputOption(
      name = "inputtext-TitleAlternate-TitleAlternate",
      id = "TitleAlternate",
      fieldType = "inputText",
      errorMessage = s"There was no text entered for the Alternate Title."
    ),
    MockInputOption(
      name = "inputtext-DescriptionAlternate-DescriptionAlternate",
      id = "DescriptionAlternate",
      fieldType = "inputText",
      errorMessage = s"There was no text entered for the Alternate Description."
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
