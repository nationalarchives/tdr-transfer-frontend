package testUtils

import org.scalatest.Assertion
import org.scalatest.matchers.must.Matchers._

class FormTester(options: List[MockInputOption], smallCheckbox: String=" govuk-checkboxes--small") {
  def generateWaysToIncorrectlySubmitAForm(): Seq[Seq[(String, String)]] = {
    val possibleOptions: Seq[String] = options.map(_.name)
    val optionsToSelectToGenerateFormErrors =
      for {
        numberRangeOfOptionsToSelect <- (1 until possibleOptions.length).toList
        optionsToSelect <- possibleOptions.combinations(numberRangeOfOptionsToSelect)
      } yield optionsToSelect.map(option => (option, "true"))

    optionsToSelectToGenerateFormErrors
  }

  def checkHtmlForOptionAndItsAttributes(htmlAsString: String,
                                         optionsSelected: Map[String, String]=Map(),
                                         formStatus: String="NotSubmitted"): Unit = {

    assert(checkIfCorrectOptionsWerePassedIntoForm(optionsSelected),
      s"\nThe option(s) selected ${optionsSelected.keys.mkString(", ")}, do not match the options passed into this class")

    options.foreach {
      option =>
        val htmlErrorSummary = s"""                    <a href="#error-${option.name}">${option.errorMessage}</a>"""
        val htmlErrorMessage =
          s"""    <p class="govuk-error-message" id="error-${option.name}">
             |        <span class="govuk-visually-hidden">Error:</span>
             |        ${option.errorMessage}
             |    </p>""".stripMargin
        formStatus match {
          case "NotSubmitted" =>
            val expectedHtmlForOption = addValuesToAttributes(option)
            checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage, formNotSubmitted=true)
          case "PartiallySubmitted" =>
            if(optionsSelected.contains(option.name)) {
              val expectedHtmlForOption = addValuesToAttributes(option, selected=true)
              checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage)
            } else {
              val expectedHtmlForOption = addValuesToAttributes(option)
              checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage, elementSelected=false)
            }
          case "Submitted" =>
            val expectedHtmlForOption = addValuesToAttributes(option, selected=true, "disabled")
            checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage)
          case _ => throw new IllegalStateException(
            s"Unexpected formStatus: $formStatus. statuses can only be 'NotSubmitted', 'PartiallySubmitted' and 'Submitted'"
          )
        }
    }
  }

  private def checkIfCorrectOptionsWerePassedIntoForm(optionsSelected: Map[String, String]): Boolean =
    optionsSelected.keys.toList.forall(
      optionSelected => options.map(_.name).contains(optionSelected)
    )

  private def addValuesToAttributes(option: MockInputOption, selected: Boolean=false, disabledStatus: String=""): String = {
    option.fieldType match {
      case "inputCheckbox" => addValuesToCheckBoxAttributes(option.name, option.label, selected || option.selected, disabledStatus)
      case "inputDate" => addValuesToDateAttributes(option.id, option.name, option.value, option.placeholder)
      case "inputDropdown" => addValuesToDropdownAttributes(option.id, option.name, selected || option.selected, option.value)
      case "inputNumeric" => addValuesToTextBoxAttributes(option.id, option.name, option.value, option.placeholder, option.fieldType)
      case "inputRadio" => addValuesToRadioAttributes(option.id, option.name, option.label, selected || option.selected, option.value: String)
      case "inputText" => addValuesToTextBoxAttributes(option.id, option.name, option.value, option.placeholder, option.fieldType)
    }
  }

  private def addValuesToCheckBoxAttributes(name: String, label: String, checked: Boolean, disabledStatus: String="") = {
    val checkedStatus = if(checked) "checked" else ""
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

  private def addValuesToDateAttributes(id: String, name: String, value: String, placeholder: String): String = {
    s"""                        <input class="govuk-input
       |                                      govuk-date-input__input
       |                                      govuk-input--width-${if(placeholder.length > 2) 3 else 2}
       |                                      "
       |                               id="$id"
       |                               name="$name"
       |                               value="$value"
       |                               type="number"
       |                               inputmode="numeric"
       |                               placeholder="$placeholder"
       |                               maxlength="${placeholder.length}"
       |                        >""".stripMargin
  }

  private def addValuesToDropdownAttributes(id: String, name: String, selected: Boolean, value: String): String = {
//    s"""    <select class="govuk-select" id="$id" name="$name"  >""".stripMargin
    val selectedStatus = if(selected) """selected="selected" """ else ""

    s"""                <option ${selectedStatus}value="$value">$value</option>""".stripMargin
  }

  private def addValuesToTextBoxAttributes(id: String, name: String, value: String,
                                           placeholder: String, fieldType: String): String = {
    val (inputType, inputMode) = fieldType match {
      case "InputNumeric" => ("number", "numeric")
      case "InputText" => ("text", "text")
    }
    s"""            <input
      |                class="govuk-input govuk-input--width-5 "
      |                id="$id"
      |                name="$name"
      |                type=$inputType
      |                value="$value"
      |                placeholder="$placeholder"
      |                inputmode=$inputMode
      |            >""".stripMargin
  }

  private def addValuesToRadioAttributes(id: String, name: String, label: String, selected: Boolean, value: String): String = {
    val selectedStatus = if(selected) "checked" else ""
    s"""            <div class="govuk-radios__item">
      |                <input
      |                        class="govuk-radios__input"
      |                        id="$id"
      |                        name="$name"
      |                        type="radio"
      |                        value="$value"
      |........................
      |                        $selectedStatus
      |                        required
      |                />""".stripMargin.replace("........................", "                        ")
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

case class MockInputOption(name: String,
                           label: String="",
                           id: String="",
                           value: String="",
                           placeholder: String="",
                           fieldType: String="",
                           errorMessage: String="",
                           selected: Boolean=false)
