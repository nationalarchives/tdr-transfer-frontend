package util
import org.scalatest.Matchers._
import org.scalatest.MustMatchers.convertToAnyMustWrapper

class CheckHtmlOfFormOptions(options: Map[String, String]) {
  def checkForOptionAndItsAttributes(htmlAsString: String,
                                     optionsSelected: Map[String, String]=Map(),
                                     formSuccessfullySubmitted: Boolean=false): Unit = {

    def addValuesToAttributes(name: String, label: String, checkedStatus: String="", disabledStatus: String=""): String = {
      s"""
         |        <div class='govuk-checkboxes__item '>
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

    options.foreach {
      case (optionName, label) =>
        if(formSuccessfullySubmitted) {
          val expectedHtmlForOption = addValuesToAttributes(optionName, label, "checked", "disabled")
          htmlAsString must include(expectedHtmlForOption)
        }
        else {
          if(optionsSelected.contains(optionName)) {
            val expectedHtmlForOption = addValuesToAttributes(optionName, label, "checked")
            htmlAsString must include(expectedHtmlForOption)
          } else {
            val expectedHtmlForOption = addValuesToAttributes(optionName, label)
            htmlAsString must include(expectedHtmlForOption)
          }
        }
    }
  }
}
