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

    "not render 'required' on html 'select' tag when set to 'false'" in {
      val args: Map[Symbol, Any] = Map('_requiredOption -> false)

      val result = FormFunctions.inputRenderOptions(args).requiredInput()
      result shouldBe ""
    }

    "not render 'required' on html 'select' tag when no '_requiredOption" in {
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

    "not render an asterisk when set to 'false'" in {
      val args: Map[Symbol, Any] = Map('_requiredOption -> false)

      val result = FormFunctions.inputRenderOptions(args).requiredLabelSuffix()
      result shouldBe ""
    }

    "not render an asterisk when no '_requiredOption" in {
      val args: Map[Symbol, Any] = Map('_madeUpOption -> true)

      val result = FormFunctions.inputRenderOptions(args).requiredLabelSuffix()
      result shouldBe ""
    }
  }

  "disabledInput function" should {
    "render disabled when '_disabledOption set to \"false\"" in {
      val args: Map[Symbol, Any] = Map('_disabledOption -> "false")

      val result = FormFunctions.inputRenderOptions(args).disabledInput("false")
      result shouldBe Some("disabled")
    }

    "not render disabled when no '_disabledOption" in {
      val args: Map[Symbol, Any] = Map('_madeUpOption -> "true")

      val result = FormFunctions.inputRenderOptions(args).disabledInput("true")
      result shouldBe ()
    }
  }

}
