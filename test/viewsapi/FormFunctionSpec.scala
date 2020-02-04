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

    "does not render 'required' on html 'select' tag when no '_requiredOption" in {
      val args: Map[Symbol, Any] = Map('_madeUpOption -> true)

      val result = FormFunctions.inputRenderOptions(args).requiredInput()
      result shouldBe ""
    }
  }

  "requiredLabelSuffix function" should {
    "render an asterisk when set to 'true'" in {
      val args: Map[Symbol, Any] = Map('_requiredOption -> true)

      val result = FormFunctions.inputRenderOptions(args).requiredLabelSuffix()
      result shouldBe "*"
    }

    "does not render an asterisk when set to 'false'" in {
      val args: Map[Symbol, Any] = Map('_requiredOption -> false)

      val result = FormFunctions.inputRenderOptions(args).requiredLabelSuffix()
      result shouldBe ""
    }

    "does not render an asterisk when no '_requiredOption" in {
      val args: Map[Symbol, Any] = Map('_madeUpOption -> true)

      val result = FormFunctions.inputRenderOptions(args).requiredLabelSuffix()
      result shouldBe ""
    }
  }
}
