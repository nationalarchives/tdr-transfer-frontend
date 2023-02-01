package testUtils

import org.scalatest.matchers.must.Matchers._
import testUtils.DefaultMockFormOptions.MockInputOption

class FormTester(defaultOptions: List[MockInputOption], smallCheckbox: String = " govuk-checkboxes--small") {
  def generateOptionsToSelectToGenerateFormErrors(value: String = "true", combineOptionNameWithValue: Boolean = false): Seq[Seq[(String, String)]] = {
    val possibleOptions: Seq[String] = defaultOptions.map(_.name)
    for {
      numberRangeOfOptionsToSelect <- (1 until possibleOptions.length).toList
      optionsToSelect <- possibleOptions.combinations(numberRangeOfOptionsToSelect)
    } yield optionsToSelect.map(option => (option, if (combineOptionNameWithValue) s"$option $value" else value))
  }

  def checkHtmlForOptionAndItsAttributes(htmlAsString: String, optionsSelected: Map[String, String], formStatus: String = "NotSubmitted"): Unit = {

    assert(
      checkIfCorrectOptionsWerePassedIntoForm(optionsSelected),
      s"\nThe option(s) selected ${optionsSelected.keys.mkString(", ")}, do not match the options passed into this class"
    )
    val optionNames: Seq[String] = defaultOptions.map(_.name)
    defaultOptions.foreach { defaultOption =>
      val (htmlErrorSummary, htmlErrorMessage) = generateErrorMessages(defaultOption)
      val selectedValue = optionsSelected.getOrElse(defaultOption.name, "OptionNotSubmitted")
      val optionStatus: OptionStatus = generateOptionStatus(defaultOption, selectedValue)

      formStatus match {
        case "NotSubmitted" =>
          val value = if (optionStatus.valueHasBeenEnteredOrSelected) selectedValue else defaultOption.value
          val valueIsSelectedOrIsPlaceholder = optionStatus.valueHasBeenEnteredOrSelected || defaultOption.placeholder.nonEmpty
          val expectedHtmlForOption = addValuesToAttributes(defaultOption, value, selected = valueIsSelectedOrIsPlaceholder, submitAttempted = false)
          checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage, formNotSubmitted = true)
        case "PartiallySubmitted" =>
          val hasErrorDependency: Boolean = hasAnErrorDependency(optionsSelected, defaultOption.errorMessageDependency)
          if (optionStatus.valueHasBeenEnteredOrSelected) {
            val expectedHtmlForOption = addValuesToAttributes(defaultOption, selectedValue, selected = true, hasDependency = hasErrorDependency)
            checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage)
          } else if (optionStatus.aDifferentValueFromSameGroupHasBeenSelected) {
            // option is part of a group with the same option.name (like radio) but a different option from group was selected
            val expectedHtmlForOption = addValuesToAttributes(defaultOption, defaultOption.value, hasDependency = hasErrorDependency)
            val elementSelectedWasNotPlaceholder: Boolean = optionsSelected(defaultOption.name) != ""
            checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage, elementSelected = elementSelectedWasNotPlaceholder)
          } else { // either no option was submitted or no value entered (is an empty string)
            val optionDoesNotBelongToAGroup = optionNames.count(name => name == defaultOption.name) == 1
            val userHasRemovedDefaultValue = selectedValue == "" && defaultOption.value != selectedValue && optionDoesNotBelongToAGroup
            val value = if (userHasRemovedDefaultValue) selectedValue else defaultOption.value
            val expectedHtmlForOption = addValuesToAttributes(
              defaultOption,
              value,
              selected = defaultOption.placeholder.nonEmpty,
              hasDependency = hasErrorDependency
            )
            checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage, elementSelected = false, errorIsDependent = hasErrorDependency)
          }
        case "Submitted" =>
          val value = if (optionStatus.valueHasBeenEnteredOrSelected) selectedValue else defaultOption.value
          val expectedHtmlForOption = addValuesToAttributes(defaultOption, value, selected = true, disabledStatus = "disabled")
          checkPageForElements(htmlAsString, expectedHtmlForOption, htmlErrorSummary, htmlErrorMessage)
        case _ =>
          throw new IllegalStateException(
            s"Unexpected formStatus: $formStatus. statuses can only be 'NotSubmitted', 'PartiallySubmitted' and 'Submitted'"
          )
      }
    }
  }

  private def generateOptionStatus(option: MockInputOption, selectedValue: String): OptionStatus = {
    val optionWasSubmittedAndValueWasEnteredOrSelected = selectedValue != "OptionNotSubmitted" && selectedValue.nonEmpty
    val valueHasBeenEntered: Boolean = optionWasSubmittedAndValueWasEnteredOrSelected && option.value == ""
    val valueHasBeenSelected: Boolean = optionWasSubmittedAndValueWasEnteredOrSelected && selectedValue.split(",").toList.contains(option.value)
    val valueHasBeenEnteredOrSelected = valueHasBeenEntered || valueHasBeenSelected
    val aDifferentValueFromSameGroupHasBeenSelected: Boolean =
      optionWasSubmittedAndValueWasEnteredOrSelected && selectedValue != option.value
    // options can have the same option.name, which means they are in a group (e.g radio options), but not value
    OptionStatus(valueHasBeenEnteredOrSelected, aDifferentValueFromSameGroupHasBeenSelected)
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
      case None                  => false // not dependent
    }

  private def checkIfCorrectOptionsWerePassedIntoForm(optionsSelected: Map[String, String]): Boolean =
    optionsSelected.keys.toList.forall(optionSelected => defaultOptions.map(_.name).contains(optionSelected))

  private def addValuesToAttributes(
      option: MockInputOption,
      valueEnteredOrSelected: String,
      submitAttempted: Boolean = true,
      selected: Boolean = false,
      hasDependency: Boolean = false,
      disabledStatus: String = ""
  ): String = {

    option.fieldType match {
      case "inputCheckbox"    => addValuesToCheckBoxAttributes(option.name, option.label, selected, disabledStatus)
      case "inputDate"        => addValuesToDateAttributes(option.id, option.name, valueEnteredOrSelected, option.placeholder, hasDependency, submitAttempted)
      case "inputmultiselect" => addValuesToMultiSelectAttributes(selected, valueEnteredOrSelected, option.id, option.name)
      case "inputDropdown"    => addValuesToDropdownAttributes(selected, valueEnteredOrSelected, option.label, option.placeholder)
      case "inputNumeric"     => addValuesToTextBoxAttributes(option.id, option.name, valueEnteredOrSelected, option.placeholder, option.fieldType, submitAttempted)
      case "inputRadio"       => addValuesToRadioAttributes(option.id, option.name, selected, valueEnteredOrSelected: String)
      case "inputText"        => addValuesToTextBoxAttributes(option.id, option.name, valueEnteredOrSelected, option.placeholder, option.fieldType, submitAttempted)
      case "inputTextArea"    => addValuesToTextAreaAttributes(option.id, option.rows, option.name, valueEnteredOrSelected, option.placeholder, option.wrap, option.maxLength)
    }
  }

  private def addValuesToCheckBoxAttributes(name: String, label: String, checked: Boolean, disabledStatus: String = "") = {
    val checkedStatus = if (checked) "checked" else ""
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

  private def addValuesToDateAttributes(id: String, name: String, value: String, placeholder: String, hasDependency: Boolean, submitAttempted: Boolean): String = {

    val doesNotHaveDependencyAndValueIsEmpty = !hasDependency && value.isEmpty
    val inputBoxShouldBeRed = submitAttempted && doesNotHaveDependencyAndValueIsEmpty

    s"""                    <input class="govuk-input
       |                                  govuk-date-input__input
       |                                  govuk-input--width-${if (placeholder.length > 2) 3 else 2}
       |                                  ${if (inputBoxShouldBeRed) "govuk-input--error" else ""}"
       |                           id="$id"
       |                           name="$name"
       |                           value="$value"
       |                           type="number"
       |                           inputmode="numeric"
       |                           placeholder="$placeholder"
       |                           maxlength="${placeholder.length}"
       |                    >""".stripMargin
  }

  private def addValuesToMultiSelectAttributes(selected: Boolean, values: String, id: String, name: String): String = {

    val status = if (selected) """checked""" else ""
    s"""                    <li class="govuk-checkboxes__item">
         |                        <input class="govuk-checkboxes__input" id="$id" name="$name" type="checkbox" value="${values}" $status>
         |                        <label class="govuk-label govuk-checkboxes__label" for="$id">${values}</label>
         |                    </li>""".stripMargin
  }

  private def addValuesToDropdownAttributes(selected: Boolean, value: String, label: String, placeholder: String): String = {
    if (placeholder.nonEmpty) {
      val selectedStatus = if (selected) "selected" else ""
      s"""    <option value="" $selectedStatus>
         |                    $placeholder""".stripMargin
    } else {
      val selectedStatus = if (selected) """selected="selected" """ else ""
      s"""                <option ${selectedStatus}value="$value">$label</option>""".stripMargin
    }
  }

  private def addValuesToTextBoxAttributes(id: String, name: String, value: String, placeholder: String, fieldType: String, submitAttempted: Boolean): String = {
    val (width, inputType, inputMode) = fieldType match {
      case "inputNumeric" => ("govuk-input--width-5", "number", "numeric")
      case "inputText"    => ("", "text", "text")
    }
    s"""        <input
       |            class="govuk-input $width ${if (submitAttempted && value.isEmpty) "govuk-input--error" else ""}"
       |            id="$id"
       |            name="$name"
       |            type="$inputType"
       |            value="$value"
       |            placeholder="$placeholder"
       |            inputmode="$inputMode"
       |        >""".stripMargin
  }

  private def addValuesToTextAreaAttributes(id: String, rows: String, name: String, value: String, placeholder: String, wrap: String, maxLength: String): String = {

    s"""<textarea class="govuk-textarea "
       |            rows="$rows"
       |            id="$id"
       |            name="$name"
       |            placeholder="$placeholder"
       |            wrap="$wrap"
       |            maxlength="$maxLength">$value</textarea>""".stripMargin
  }

  private def addValuesToRadioAttributes(id: String, name: String, selected: Boolean, value: String): String = {
    val selectedStatus = if (selected) "checked" else ""
    s"""                <input
      |                        class="govuk-radios__input"
      |                        id="$id"
      |                        name="$name"
      |                        type="radio"
      |                        value="$value"
      |                        data-aria-controls="conditional-$name-$value"
      |........................
      |                        $selectedStatus
      |                        required
      |                    />""".stripMargin.replace("........................", "                        ")
  }

  private def checkPageForElements(
      htmlAsString: String,
      expectedHtmlForOption: String,
      htmlErrorSummary: String,
      htmlErrorMessage: String,
      elementSelected: Boolean = true,
      errorIsDependent: Boolean = false, // does error message displaying depend on another field?
      formNotSubmitted: Boolean = false
  ): Any = {

    htmlAsString must include(expectedHtmlForOption)

    if (elementSelected || errorIsDependent || formNotSubmitted) {
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

case class OptionStatus(valueHasBeenEnteredOrSelected: Boolean, aDifferentValueFromSameGroupHasBeenSelected: Boolean)
