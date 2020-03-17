package viewsapi

import views.html.helper.FieldElements

object FormFunctions {
  val requiredInputArg = Symbol("_requiredOption")
  val selectedInputArg = Symbol("_checkedOption")
  val disabledInputArg = Symbol("_disabledOption")

  class InputRenderOptions(args: Map[Symbol, Any]) {

    def selectedInput(value: String): Any = {
      if(args.contains(selectedInputArg)) {
        args.get(selectedInputArg).filter( _ == value).map{_ => "checked"}
      }
    }

    def requiredInput(): String = {
      if (args.exists(_ == (requiredInputArg, true))) "required" else ""
    }

    def disabledInput(value: String): Any = {
      if(args.contains(disabledInputArg)) {
        args.get(disabledInputArg).filter( _ == value).map{_ => "disabled"}
      }
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
