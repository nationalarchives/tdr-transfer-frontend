package viewsapi

import play.twirl.api.Html
import views.html.helper.FieldElements

object FormFunctions {
  val requiredInputArg = '_requiredOption

  class InputRenderOptions(args: Map[Symbol, Any]) {

    def requiredInput(): String = {
      if (args.exists(_ == (requiredInputArg, true))) "required" else ""
    }

    def requiredLabelSuffix(): String = {
      if (args.exists(_ == (requiredInputArg, true))) "*" else ""
    }
  }
  class ErrorHandling(elements: FieldElements) {

    def setErrorClass(): String = {
      if(elements.hasErrors) {
        "govuk-form-group--error"
      } else {
        ""
      }
    }

    def renderErrorMessage(): Html = {
      if(elements.hasErrors) {
        Html("<span id=\"error\" class=\"govuk-error-message\"><span class=\"govuk-visually-hidden\">Error:</span>" +
          elements.errors.mkString(", ")
        )
      } else {
        Html("")
      }
    }
  }

  implicit def errorHandling(elements: FieldElements): ErrorHandling = new ErrorHandling(elements)

  implicit def inputRenderOptions(args: Map[Symbol, Any]): InputRenderOptions = new InputRenderOptions(args)
}
