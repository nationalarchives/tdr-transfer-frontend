package viewsapi

import org.scalatest.Matchers._
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.data.{Field, Form}
import play.api.i18n.{DefaultMessagesApi, MessagesImpl}
import play.i18n.Lang
import play.twirl.api.Html
import util.FrontEndTestHelper
import views.html.helper.FieldElements

class FormFunctionSpec extends FrontEndTestHelper {

  def getFieldElements(errors: Map[Symbol, String]): FieldElements = {
    val form = Form(
      mapping(
        "id" -> nonEmptyText
      )(TestData.apply)(TestData.unapply)
    )
    val field = Field(form, "", Seq(), Option.empty, Seq(), Option.empty)
    val fieldElements: FieldElements = new FieldElements("", field, Html(""), errors, MessagesImpl(Lang.forCode("en-gb"), new DefaultMessagesApi()))
    fieldElements
  }

  "requiredInput function" should {
    "render 'required' on html 'select' tag when set to 'true'" in {
      val args: Map[Symbol, Any] = Map(Symbol("_requiredOption") -> true)

      val result = FormFunctions.inputRenderOptions(args).requiredInput()
      result shouldBe "required"
    }

    "not render 'required' on html 'select' tag when set to 'false'" in {
      val args: Map[Symbol, Any] = Map(Symbol("_requiredOption") -> false)

      val result = FormFunctions.inputRenderOptions(args).requiredInput()
      result shouldBe ""
    }

    "not render 'required' on html 'select' tag when no '_requiredOption" in {
      val args: Map[Symbol, Any] = Map(Symbol("_madeUpOption") -> true)

      val result = FormFunctions.inputRenderOptions(args).requiredInput()
      result shouldBe ""
    }
  }

  "requiredLabelSuffix function" should {
    "render an asterisk when set to 'true'" in {
      val args: Map[Symbol, Any] = Map(Symbol("_requiredOption") -> true)

      val result = FormFunctions.inputRenderOptions(args).requiredLabelSuffix()
      result shouldBe "*"
    }

    "not render an asterisk when set to 'false'" in {
      val args: Map[Symbol, Any] = Map(Symbol("_requiredOption") -> false)

      val result = FormFunctions.inputRenderOptions(args).requiredLabelSuffix()
      result shouldBe ""
    }

    "not render an asterisk when no _requiredOption" in {
      val args: Map[Symbol, Any] = Map(Symbol("_madeUpOption") -> true)

      val result = FormFunctions.inputRenderOptions(args).requiredLabelSuffix()
      result shouldBe ""
    }
  }

  "disabledInput function" should {
    "render disabled when _disabledOption set to a particular input option" in {
      val args: Map[Symbol, Any] = Map(Symbol("_disabledOption") -> "false")

      val result = FormFunctions.inputRenderOptions(args).disabledInput("false")
      result shouldBe Some("disabled")
    }

    "not render disabled when no _disabledOption" in {
      val args: Map[Symbol, Any] = Map(Symbol("_madeUpOption") -> "true")

      val result = FormFunctions.inputRenderOptions(args).disabledInput("true")
      result shouldBe ()
    }
  }

  "selectedInput function" should {
    "render checked when _checkedOption set to a particular input option" in {
      val args: Map[Symbol, Any] = Map(Symbol("_checkedOption") -> "false")

      val result = FormFunctions.inputRenderOptions(args).selectedInput("false")
      result shouldBe Some("checked")
    }

    "not render checked when no _checkedOption" in {
      val args: Map[Symbol, Any] = Map(Symbol("_madeUpOption") -> "true")

      val result = FormFunctions.inputRenderOptions(args).selectedInput("true")
      result shouldBe ()
    }
  }

  "setErrorClass function" should {
    "return the error class if the element has errors" in {
      val errors: Map[Symbol, String] = Map(Symbol("_error") -> "Error")
      val fieldElements: FieldElements = getFieldElements(errors)
      val errorClass = viewsapi.FormFunctions.errorHandling(fieldElements).setErrorClass()
      errorClass should be("govuk-form-group--error")
    }

    "return empty string if the element has no errors" in {
      val errors: Map[Symbol, String] = Map()
      val fieldElements: FieldElements = getFieldElements(errors)
      val errorClass = viewsapi.FormFunctions.errorHandling(fieldElements).setErrorClass()
      errorClass should be("")
    }
  }

}

case class TestData(id: String)
