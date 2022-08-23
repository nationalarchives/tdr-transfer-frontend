package testUtils

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

  def checkHtmlForOptionAndItsAttributes(htmlAsString: String, optionsSelected: Map[String, String]=Map(), formStatus: String="NotSubmitted"): Unit = {

    assert(checkIfCorrectOptionsWerePassedIntoForm(optionsSelected),
      s"\nThe option(s) selected ${optionsSelected.keys.mkString(", ")}, do not match the options passed into this class")
    options.foreach {
      option =>
        val (htmlErrorSummary, htmlErrorMessage) = generateErrorMessages(option)
        val valueSelected = optionsSelected.getOrElse(option.name, "")
        formStatus match {
          case "NotSubmitted" =>
            val expectedHtmlForOption = addValuesToAttributes(option, selected=valueSelected == option.value, submitAttempted=false)
            checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage, formNotSubmitted=true)
          case "PartiallySubmitted" =>
            val optionPresentAndHasAValue = valueSelected != ""
            if(optionPresentAndHasAValue && valueSelected == option.value) {
              val expectedHtmlForOption = addValuesToAttributes(option, selected=true)
              checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage, elementSelected=option.value.nonEmpty)
            } else if(optionPresentAndHasAValue && valueSelected != option.value) { // option is part of a group; another option from group was selected
              val expectedHtmlForOption = addValuesToAttributes(option)
              val elementSelectedWasNotPlaceholder: Boolean = optionsSelected(option.name) != ""
              checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage, elementSelected = elementSelectedWasNotPlaceholder)
            } else {
              val hasErrorDependency: Boolean = hasAnErrorDependency(optionsSelected, option.errorMessageDependency)
              val expectedHtmlForOption = addValuesToAttributes(option, selected=option.placeholder.nonEmpty, hasDependency=hasErrorDependency)
              checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage, elementSelected=false,
              errorIsDependent = hasErrorDependency)
            }
          case "Submitted" =>
            val expectedHtmlForOption = addValuesToAttributes(option, selected=true, disabledStatus="disabled")
            checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage)
          case _ => throw new IllegalStateException(
            s"Unexpected formStatus: $formStatus. statuses can only be 'NotSubmitted', 'PartiallySubmitted' and 'Submitted'"
          )
        }
    }
  }

  private def generateErrorMessages(option: MockInputOption): (String, String) = {
    val errorId = if (option.name.startsWith("input")) option.name.split("-")(1) else option.name
    val htmlErrorSummary = s"""                    <a href="#error-$errorId">${option.errorMessage}</a>"""
    val htmlErrorMessage =
      s"""    <p class="govuk-error-message" id="error-$errorId">
         |        <span class="govuk-visually-hidden">Error:</span>
         |        ${option.errorMessage}
         |    </p>""".stripMargin
    (htmlErrorSummary, htmlErrorMessage)
  }

  private def hasAnErrorDependency(optionsSelected: Map[String, String], errorMessageDependency: String): Boolean =
    optionsSelected.get(errorMessageDependency) match {
      case Some(dependencyValue) => dependencyValue == ""
      case None => false // not dependent
    }

  private def checkIfCorrectOptionsWerePassedIntoForm(optionsSelected: Map[String, String]): Boolean =
    optionsSelected.keys.toList.forall(
      optionSelected => options.map(_.name).contains(optionSelected)
    )

  private def addValuesToAttributes(option: MockInputOption, submitAttempted: Boolean=true, selected: Boolean=false,
                                    hasDependency: Boolean=false, disabledStatus: String=""): String = {

    option.fieldType match {
      case "inputCheckbox" => addValuesToCheckBoxAttributes(option.name, option.label, selected, disabledStatus)
      case "inputDate" => addValuesToDateAttributes(option.id, option.name, option.value, option.placeholder, hasDependency, submitAttempted)
      case "inputDropdown" => addValuesToDropdownAttributes(selected, option.value, option.label, option.placeholder)
      case "inputNumeric" => addValuesToTextBoxAttributes(option.id, option.name, option.value, option.placeholder, option.fieldType)
      case "inputRadio" => addValuesToRadioAttributes(option.id, option.name, selected, option.value: String)
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

  private def addValuesToDateAttributes(id: String, name: String, value: String, placeholder: String, hasDependency: Boolean,
                                        submitAttempted: Boolean): String = {

    val doesNotHaveDependencyAndValueIsEmpty = !hasDependency && value.isEmpty
    val inputBoxShouldBeRed = submitAttempted && doesNotHaveDependencyAndValueIsEmpty

    s"""                        <input class="govuk-input
       |                                      govuk-date-input__input
       |                                      govuk-input--width-${if(placeholder.length > 2) 3 else 2}
       |                                      ${if(inputBoxShouldBeRed) "govuk-input--error" else ""}"
       |                               id="$id"
       |                               name="$name"
       |                               value="$value"
       |                               type="number"
       |                               inputmode="numeric"
       |                               placeholder="$placeholder"
       |                               maxlength="${placeholder.length}"
       |                        >""".stripMargin
  }

  private def addValuesToDropdownAttributes(selected: Boolean, value: String, label: String, placeholder: String): String = {
    if(placeholder.nonEmpty) {
      val selectedStatus = if(selected) "selected" else ""
      s"""    <option value="" $selectedStatus>
         |                    $placeholder""".stripMargin
    }
    else {
      val selectedStatus = if(selected) """selected="selected" """ else ""
      s"""                <option ${selectedStatus}value="$value">$label</option>""".stripMargin
    }
  }

  private def addValuesToTextBoxAttributes(id: String, name: String, value: String, placeholder: String, fieldType: String): String = {
    val (inputType, inputMode) = fieldType match {
      case "inputNumeric" => ("number", "numeric")
      case "inputText" => ("text", "text")
    }
    s"""            <input
      |                class="govuk-input govuk-input--width-5 ${if(value.isEmpty) "govuk-input--error" else ""}"
      |                id="$id"
      |                name="$name"
      |                type="$inputType"
      |                value="$value"
      |                placeholder="$placeholder"
      |                inputmode="$inputMode"
      |            >""".stripMargin
  }

  private def addValuesToRadioAttributes(id: String, name: String, selected: Boolean, value: String): String = {
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
                                   errorIsDependent:Boolean=false, // does error message displaying depend on another field?
                                   formNotSubmitted: Boolean=false): Any = {

    htmlAsString must include(expectedHtmlForOption)

    if(elementSelected || errorIsDependent || formNotSubmitted) {
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
                           errorMessageDependency: String="") // some fields (like month) can only display their error if another field (like day) has none
