package viewsapi

import play.twirl.api.Html
import views.html.helper.FieldElements

object FormFunctions {
  val requiredInputArg = Symbol("_requiredOption")

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
  }

  implicit def errorHandling(elements: FieldElements): ErrorHandling = new ErrorHandling(elements)

  implicit def inputRenderOptions(args: Map[Symbol, Any]): InputRenderOptions = new InputRenderOptions(args)
}
