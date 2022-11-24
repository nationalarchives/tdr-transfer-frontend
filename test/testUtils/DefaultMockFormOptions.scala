package testUtils

import java.util.UUID

object DefaultMockFormOptions {

  lazy val expectedPrivateBetaOptions: List[MockInputOption] = List(
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
    ),
    MockInputOption(
      "english",
      "I confirm that the records are all in English.",
      value = "true",
      errorMessage = "All records must be confirmed as English language before proceeding",
      fieldType = "inputCheckbox"
    )
  )

  lazy val expectedComplianceOptions: List[MockInputOption] = List(
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
    ),
    MockInputOption(
      "openRecords",
      "I confirm that all records are open and no Freedom of Information (FOI) exemptions apply to these records.",
      value = "true",
      errorMessage = "All records must be open",
      fieldType = "inputCheckbox"
    )
  )

  val expectedClosureDefaultOptions: List[MockInputOption] = List(
    MockInputOption(
      name = "inputdate-ClosureStartDate-day",
      label = "Day",
      id = "date-input-ClosureStartDate-day",
      placeholder = "dd",
      fieldType = "inputDate",
      errorMessage = s"There was no number entered for the Day."
    ),
    MockInputOption(
      name = "inputdate-ClosureStartDate-month",
      label = "Month",
      id = "date-input-ClosureStartDate-month",
      placeholder = "mm",
      fieldType = "inputDate",
      errorMessage = s"There was no number entered for the Month.",
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
      errorMessage = s"There was no number entered for the Day."
    ),
    MockInputOption(
      name = "inputdate-FoiExemptionAsserted-month",
      label = "Month",
      id = "date-input-FoiExemptionAsserted-month",
      placeholder = "mm",
      fieldType = "inputDate",
      errorMessage = s"There was no number entered for the Month.",
      errorMessageDependency = "inputdate-FoiExemptionAsserted-day"
    ),
    MockInputOption(
      name = "inputdate-FoiExemptionAsserted-year",
      label = "Year",
      id = "date-input-FoiExemptionAsserted-year",
      placeholder = "yyyy",
      fieldType = "inputDate",
      errorMessage = s"There was no number entered for the Year.",
      errorMessageDependency = "inputdate-FoiExemptionAsserted-month"
    ),
    MockInputOption(
      name = "inputnumeric-ClosurePeriod-years",
      label = "years",
      id = "years",
      fieldType = "inputNumeric",
      errorMessage = s"There was no number entered for the Closure period."
    ),
    MockInputOption(
      name = "inputcheckbox-FoiExemptionCode",
      id = "inputcheckbox-FoiExemptionCode",
      label = "mock code1",
      value = "mock code1",
      fieldType = "inputCheckbox",
      errorMessage = "Invalid option was provided for FOI exemption code."
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
      id = "inputradio-TitleClosed-No",
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
      "openRecords",
      "I confirm that all records are open and no Freedom of Information (FOI) exemptions apply to these records.",
      value = "true",
      errorMessage = "All records must be confirmed as open before proceeding",
      fieldType = "inputCheckbox"
    ),
    MockInputOption(
      "transferLegalCustody",
      "I confirm that I am transferring legal custody of these records to The National Archives.",
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
      errorMessageDependency: String = ""
  ) // some fields (like month) can only display their error if another field (like day) has none
}
