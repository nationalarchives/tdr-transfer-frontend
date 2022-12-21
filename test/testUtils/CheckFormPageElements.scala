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
      |            Date of the Advisory Council approval (or SIRO approval if appropriate)
      |        </div>""",
    """            <h2 class="govuk-label govuk-label--m">
      |                Closure start date
      |            </h2>""",
    """        <div id="date-input-ClosureStartDate-hint" class="govuk-hint">
      |            This has been defaulted to the last date modified. If this is not correct, amend the field below.
      |        </div>""",
    """        <label class="govuk-label govuk-label--m" for=years>
      |            Closure period
      |        </label>""",
    """    <div id="numeric-input-hint" class="govuk-hint">
      |        Number of years the record is closed from the closure start date
      |    </div>""",
    """            <label class="govuk-label govuk-label--m" for="inputmultiselect-FoiExemptionCode">
      |                FOI exemption code
      |            </label>""",
    """        <div id="inputmultiselect-FoiExemptionCode-hint" class="govuk-hint">
      |            Add one or more exemption code to this closure. Here is a<a target="_blank" href="https://www.legislation.gov.uk/ukpga/2000/36/contents">full list of FOI codes and their designated exemptions</a>.
      |        </div>""",
    """        <div class="tna-multi-select-search__filter">
      |            <label for="input-filter" class="govuk-label govuk-visually-hidden">Filter </label>
      |            <input name="tna-multi-select-search" id="input-filter" class="tna-multi-select-search__filter-input govuk-input" type="text" aria-describedby="FoiExemptionCode-filter-count" placeholder="Filter FOI exemption code">
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

  def checkFormContent(metadataType: String, formPageAsFormattedString: String): Unit = {
    val formPageAsString = formPageAsFormattedString.replaceAll(twoOrMoreSpaces, "")
    val expectedFormHtmlElements = if (metadataType == "descriptive") { Set() }
    else { expectedClosureFormHtmlElements }

    expectedFormHtmlElements.foreach { htmlElement =>
      formPageAsString must include(
        htmlElement.stripMargin.replaceAll(twoOrMoreSpaces, "")
      )
    }
  }
}
