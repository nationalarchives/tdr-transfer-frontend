package viewsapi

import org.scalatest.matchers.should.Matchers._
import play.api.data.Forms.{mapping, nonEmptyText}
import play.api.data.{Field, Form}
import play.api.i18n.{DefaultMessagesApi, MessagesImpl}
import play.i18n.Lang
import play.twirl.api.Html
import testUtils.FrontEndTestHelper
import views.html.helper.FieldElements

class FormFunctionSpec extends FrontEndTestHelper {

  val form: Form[TestData] = Form(
    mapping(
      "id" -> nonEmptyText
    )(TestData.apply)(TestData.unapply)
  )

  def getFieldElements(errors: Map[Symbol, String]): FieldElements = {
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

  "shouldOptionBeSelected" should {
    "return checked if the form is already submitted" in {
      val res = FormFunctions
        .formDataOptions(form)
        .shouldOptionBeSelected("id", formAlreadySubmitted = true)
      res should be("checked")
    }

    "return checked if the option is selected" in {
      val filledForm = form.fill(TestData("true"))
      val res = FormFunctions
        .formDataOptions(filledForm)
        .shouldOptionBeSelected("id")
      res should be("checked")
    }

    "return empty if the option is not selected" in {
      val res = FormFunctions
        .formDataOptions(form)
        .shouldOptionBeSelected("id")
      res should be("")
    }

    "return empty if the option does not exist" in {
      val res = FormFunctions
        .formDataOptions(form)
        .shouldOptionBeSelected("invalid")
      res should be("")
    }

    "return exception if the option selected exists but value is not 'true' or 'false'" in {
      val filledForm = form.fill(TestData("invalid"))
      val thrownException =
        the[IllegalStateException] thrownBy FormFunctions.formDataOptions(filledForm).shouldOptionBeSelected("id")

      thrownException.getMessage should equal("Unexpected value: Some(invalid). value must be 'true' or 'false'")
    }
  }
}

case class TestData(id: String)
