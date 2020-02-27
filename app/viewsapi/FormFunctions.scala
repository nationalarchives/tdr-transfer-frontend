package viewsapi

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

  implicit def inputRenderOptions(args: Map[Symbol, Any]): InputRenderOptions = new InputRenderOptions(args)
}
