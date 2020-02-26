package viewsapi

object FormFunctions {
  val selectedInputArg = '_checkedOption
  val requiredInputArg = '_requiredOption
  val disabledInputArg = '_disabledOption

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

  implicit def inputRenderOptions(args: Map[Symbol, Any]): InputRenderOptions = new InputRenderOptions(args)
}
