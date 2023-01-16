package viewsapi

import play.api.data.Form
import views.html.helper.FieldElements

object FormFunctions {
  val requiredInputArg: Symbol = Symbol("_requiredOption")

  class FormDataOptions[T](args: Form[T]) {

    def shouldOptionBeSelected(option: String, formAlreadySubmitted: Boolean = false): String = {
      if (formAlreadySubmitted) {
        "checked"
      } else {
        val optionSelected = args.data.get(option)
        optionSelected match {
          case Some("true") | Some("false") => "checked" // we're only using 'true' values for now but might use 'false' in future
          case None                         => ""
          case _                            => throw new IllegalStateException(s"Unexpected value: $optionSelected. value must be 'true' or 'false'")
        }
      }
    }
  }

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
      if (elements.hasErrors) {
        "govuk-form-group--error"
      } else {
        ""
      }
    }
  }

  implicit def errorHandling(elements: FieldElements): ErrorHandling = new ErrorHandling(elements)

  implicit def inputRenderOptions(args: Map[Symbol, Any]): InputRenderOptions = new InputRenderOptions(args)

  implicit def formDataOptions[T](args: Form[T]): FormDataOptions[T] = new FormDataOptions(args)
}
