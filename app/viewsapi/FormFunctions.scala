package viewsapi

object FormFunctions {
  val requiredInputArg = '_requiredOption

  class InputRenderOptions(args: Map[Symbol, Any]) {

    def requiredInput(): String = {
      if (args.exists(_ == (requiredInputArg, true))) "required" else ""
    }
  }

  implicit def inputRenderOptions(args: Map[Symbol, Any]): InputRenderOptions = new InputRenderOptions(args)
}
