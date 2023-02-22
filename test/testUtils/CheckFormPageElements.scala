package testUtils

import org.scalatest.matchers.must.Matchers.{convertToAnyMustWrapper, include}

class CheckFormPageElements() {
  val twoOrMoreSpaces = "\\s{2,}"

  private val expectedClosureFormHtmlElements = Set(
    """      <title>Add or edit closure metadata</title>""",
    """      <span class="govuk-caption-l">Closure metadata</span>""",
    """      <h1 class="govuk-heading-l">Add or edit metadata</h1>""",
    """            <h2 class="govuk-label govuk-label--m">
      |                FOI decision asserted
      |            </h2>""",
    """        <div id="date-input-FoiExemptionAsserted-hint" class="govuk-hint">
      |            Date of the Advisory Council approval
      |        </div>""",
    """            <h2 class="govuk-label govuk-label--m">
      |                Closure Start Date
      |            </h2>""",
    """        <div id="date-input-ClosureStartDate-hint" class="govuk-hint">
      |            This has been defaulted to the last date modified. If this is not correct, amend the field below.
      |        </div>""",
    """        <label class="govuk-label govuk-label--m" for=years>
      |           Closure Period
      |        </label>""",
    """    <div id="numeric-input-hint" class="govuk-hint">
      |        Number of years the record is closed from the closure start date
      |    </div>""",
    """        <label class="govuk-label govuk-label--m" for="inputmultiselect-FoiExemptionCode">
      |           FOI exemption code(s)
      |         </label>""",
    """        <div id="inputmultiselect-FoiExemptionCode-hint" class="govuk-hint">
      |        Add one or more exemption code to this closure. Here is a <a target="_blank" href="https://www.legislation.gov.uk/ukpga/2000/36/contents">full list of FOI codes and their designated exemptions</a>
      |        </div>""",
    """        <div class="tna-multi-select-search__filter">
      |            <label for="input-filter" class="govuk-label govuk-visually-hidden">Start typing to filter languages</label>
      |            <input name="tna-multi-select-search" id="input-filter" class="tna-multi-select-search__filter-input govuk-input" type="text" aria-describedby="FoiExemptionCode-filter-count" placeholder="Search by typing an FOI Exemption code">
      |        </div>""".stripMargin,
    """<span id="FoiExemptionCode-filter-count" class="govuk-visually-hidden js-filter-count" aria-live="polite"></span>""",
    """<ul class="govuk-checkboxes tna-multi-select-search__list"
      |            id="FoiExemptionCode" aria-describedby="FoiExemptionCode-filter-count">""".stripMargin,
    """        <legend class="govuk-fieldset__legend govuk-fieldset__legend--m">
      |            Is the title closed?
      |        </legend>""",
    """        <legend class="govuk-fieldset__legend govuk-fieldset__legend--m">
      |            Is the description closed?
      |        </legend>"""
  )

  private val expectedDescriptiveFormHtmlElements = Set(
    """      <title>Add or edit descriptive metadata</title>""",
    """      <span class="govuk-caption-l">Descriptive metadata</span>""",
    """      <h1 class="govuk-heading-l">Add or edit metadata</h1>""",
    """      <h2 class="govuk-label govuk-label--m">Date of the record</h2>""",
    """      <div id="date-input-end_date-hint" class="govuk-hint">The date the most recent change was made to the record</div>""",
    """        <label class="govuk-label govuk-label--m" for=inputtextarea-description>
      |            Description
      |        </label>""",
    """<span class="govuk-details__summary-text">A details summary</span>""",
    """<div class="govuk-details__text"><p class="govuk-body">A details text</p></div>""",
    """    <div id="inputtextarea-description-hint" class="govuk-hint">
      |        This description will be visible on Discovery and help explain the content of your file(s).
      |    </div>""",
    """        <h2 class="govuk-label-wrapper">
      |            <label class="govuk-label govuk-label--m" for="inputmultiselect-Language">
      |                Language
      |            </label>
      |        </h2>""",
    """        <div id="inputmultiselect-Language-hint" class="govuk-hint">""",
    """                Choose one or more languages used in this record.""",
    """        </div>"""
  )

  def checkFormContent(metadataType: String, formPageAsFormattedString: String): Unit = {
    val formPageAsString = formPageAsFormattedString.replaceAll(twoOrMoreSpaces, "")
    val expectedFormHtmlElements = if (metadataType == "descriptive") { expectedDescriptiveFormHtmlElements }
    else { expectedClosureFormHtmlElements }

    expectedFormHtmlElements.foreach { htmlElement =>
      formPageAsString must include(
        htmlElement.stripMargin.replaceAll(twoOrMoreSpaces, "")
      )
    }
  }
}
