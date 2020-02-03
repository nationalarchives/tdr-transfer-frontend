package viewsapi

import util.FrontEndTestHelper
import org.scalatest.Matchers._

class FormFunctionSpec extends FrontEndTestHelper  {
  "requiredInput function" should {
    "render 'required' on html 'select' tag when set to 'true'" in {
      val args: Map[Symbol, Any] = Map('_requiredOption -> true)

      val result = FormFunctions.inputRenderOptions(args).requiredInput()
      result shouldBe "required"
    }

    "does not render 'required' on html 'select' tag when set to 'false'" in {
      val args: Map[Symbol, Any] = Map('_requiredOption -> false)

      val result = FormFunctions.inputRenderOptions(args).requiredInput()
      result shouldBe ""
    }

    "render 'required' on html 'select' tag when value is empty String" in {
      val args: Map[Symbol, Any] = Map('_requiredOption -> "")

      val result = FormFunctions.inputRenderOptions(args).requiredInput()
      result shouldBe ""
    }
  }
}
