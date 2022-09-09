package controllers.util

import play.api.mvc.{AnyContent, Request}

class DynamicFormUtils(request: Request[AnyContent], defaultFieldValues: List[FormField]) {
  private val formAnswers: Map[String, Seq[String]] = request.body.asFormUrlEncoded match {
    case Some(answers: Map[String, Seq[String]]) => answers
    case _ => throw new Exception("Error: There were no values submitted.") // This should never happen
  }

  lazy val formAnswersWithValidInputNames: Map[String, Seq[String]] = formAnswers.filter {
    case (inputName, _) if inputName.startsWith("input") => true
    case (inputName, _) if inputName == "csrfToken" => false
    case (inputName, _) => throw new IllegalArgumentException(s"${inputName.split("-").head} is not a supported field type.")
  }

  def validateAndConvertSubmittedValuesToFormFields(submittedValues: Map[String, Seq[String]]): List[FormField] = {
    defaultFieldValues.map {
      formField => {
        val fieldValue: List[(String, Seq[String])] = getSubmittedFieldValue(formField.fieldId, submittedValues)
        formField match {
          case dateField: DateField =>
            val day = fieldValue.find(_._1.endsWith("day")).map(_._2.head).getOrElse("")
            val month = fieldValue.find(_._1.endsWith("month")).map(_._2.head).getOrElse("")
            val year = fieldValue.find(_._1.endsWith("year")).map(_._2.head).getOrElse("")
            DateField.update(dateField, day, month, year)
              .copy(fieldErrors = DateField.validate(day, month, year).map(List(_)).getOrElse(Nil))

          case radioButtonGroupField: RadioButtonGroupField =>
            val selectedOption = fieldValue.head._2.headOption.getOrElse("")
            radioButtonGroupField.copy(selectedOption = selectedOption)

          case textField: TextField =>
            val text = fieldValue.head._2.head
            TextField.update(textField, text)
              .copy(fieldErrors = TextField.validate(text, textField).map(List(_)).getOrElse(Nil))

          case dropdownField: DropdownField =>
            val text = fieldValue.head._2.headOption.getOrElse("")
            DropdownField.update(dropdownField, text)
              .copy(fieldErrors = DropdownField.validate(text, dropdownField).map(List(_)).getOrElse(Nil))
        }
      }
    }
  }

  private def getSubmittedFieldValue(fieldId: String, submittedValues: Map[String, Seq[String]]): List[(String, Seq[String])] = {
    val fieldValue = submittedValues.filter(_._1.contains(fieldId)).toList
    if (fieldValue.isEmpty) {
      throw new IllegalArgumentException(s"Metadata name $fieldId does not exist in submitted form values")
    } else {
      fieldValue
    }
  }
}
