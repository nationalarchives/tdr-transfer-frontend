package util

import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers._

class CheckFormOptionsHtml(options: Map[String, (String, String)], smallCheckbox: String=" govuk-checkboxes--small") {
  def generateWaysToIncorrectlySubmitAForm(): Seq[Seq[(String, String)]] = {
    val possibleOptions: Seq[String] = options.keys.toList
    val optionsToSelectToGenerateFormErrors =
      for {
        numberRangeOfOptionsToSelect <- (1 until possibleOptions.length).toList
        optionsToSelect <- possibleOptions.combinations(numberRangeOfOptionsToSelect)
      } yield optionsToSelect.map(option => (option, "true"))

    optionsToSelectToGenerateFormErrors
  }

  def checkForOptionAndItsAttributes(htmlAsString: String,
                                     optionsSelected: Map[String, String]=Map(),
                                     formStatus: String="NotSubmitted"): Unit = {

    assert(checkIfCorrectOptionsWerePassedIntoForm(optionsSelected),
      s"\nThe option(s) selected ${optionsSelected.keys.mkString(", ")}, do not match the options passed into this class")

    options.foreach {
      case (optionName, (label, errorMessage) ) =>
        val htmlErrorSummary = s"""                        <a href="#error-$optionName">$errorMessage</a>"""
        val htmlErrorMessage =
          s"""    <p class="govuk-error-message" id="error-$optionName">
             |        <span class="govuk-visually-hidden">Error:</span>
             |        $errorMessage
             |    </p>""".stripMargin
        formStatus match {
          case "NotSubmitted" =>
            val expectedHtmlForOption = addValuesToAttributes(optionName, label)
            checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage, formNotSubmitted=true)
          case "PartiallySubmitted" =>
            if(optionsSelected.contains(optionName)) {
              val expectedHtmlForOption = addValuesToAttributes(optionName, label, "checked")
              checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage)
            } else {
              val expectedHtmlForOption = addValuesToAttributes(optionName, label)
              checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage, elementSelected=false)
            }
          case "Submitted" =>
            val expectedHtmlForOption = addValuesToAttributes(optionName, label, "checked", "disabled")
            checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage)
          case _ => throw new IllegalStateException(
            s"Unexpected formStatus: $formStatus. statuses can only be 'NotSubmitted', 'PartiallySubmitted' and 'Submitted'"
          )
        }
    }
  }

  private def checkIfCorrectOptionsWerePassedIntoForm(optionsSelected: Map[String, String]): Boolean =
    optionsSelected.keys.toList.forall(
      optionSelected => options.keys.toList.contains(optionSelected)
    )

  private def addValuesToAttributes(name: String,
                                        label: String,
                                        checkedStatus: String="",
                                        disabledStatus: String=""): String = {
    s"""
       |        <div class='govuk-checkboxes__item$smallCheckbox'>
       |            <input
       |                $checkedStatus
       |                class="govuk-checkboxes__input"
       |                id="$name"
       |                name="$name"
       |                type="checkbox"
       |                value="true"
       |                $disabledStatus />
       |            <label class="govuk-label govuk-checkboxes__label" for="$name">
       |                $label""".stripMargin
  }

  private def checkPageForElements(htmlAsString: String,
                           expectedHtmlForOption: String,
                           htmlErrorSummary: String,
                           htmlErrorMessage: String,
                           elementSelected: Boolean=true,
                           formNotSubmitted: Boolean=false): Assertion = {

    htmlAsString must include(expectedHtmlForOption)

    if(elementSelected || formNotSubmitted) {
      htmlAsString must not include htmlErrorSummary
      htmlAsString must not include htmlErrorMessage
    } else {
      htmlAsString must include(
        """<h2 class="govuk-error-summary__title" id="error-summary-title">
          |            There is a problem
          |        </h2>""".stripMargin
      )
      htmlAsString must include(htmlErrorSummary)
      htmlAsString must include(htmlErrorMessage)
    }
  }
}
