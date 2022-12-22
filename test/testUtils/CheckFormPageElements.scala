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
    """            <label class="govuk-label govuk-label--m" for="inputdropdown-FoiExemptionCode">
      |                FOI exemption code
      |            </label>""".replace("................", "                "),
    """        <div id="inputdropdown-FoiExemptionCode-hint" class="govuk-hint">
      |            Add one or more exemption code to this closure. Here is a<a target="_blank" href="https://www.legislation.gov.uk/ukpga/2000/36/contents">full list of FOI codes and their designated exemptions</a>.
      |        </div>""",
    """<select class="govuk-select" id="inputdropdown-FoiExemptionCode" name="inputdropdown-FoiExemptionCode"  >""",
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
    """        <label class="govuk-label govuk-label--m" for=inputtextarea-description>
      |            Description
      |        </label>""",
    """    <div id="inputtextarea-description-hint" class="govuk-hint">
      |        This description will be visible on Discovery and help explain the content of your file(s).
      |    </div>""",
    """            <label class="govuk-label govuk-label--m" for="inputdropdown-Language">
      |                Language
      |            </label>""".replace("................", "                "),
    """        <div id="inputdropdown-Language-hint" class="govuk-hint">
      |            Choose one or more languages used in this record.
      |        </div>""",
    """<select class="govuk-select" id="inputdropdown-Language" name="inputdropdown-Language"  >"""
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
