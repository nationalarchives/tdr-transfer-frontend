package controllers.util

import play.api.mvc.{AnyContent, Request}

class DynamicFormUtils(request: Request[AnyContent], defaultFieldValues: List[FormField]) {
  private val formAnswers: Map[String, Seq[String]] = request.body.asFormUrlEncoded match {
    case Some(answers: Map[String, Seq[String]]) => answers
    case _                                       => throw new Exception("Error: There were no values submitted.") // This should never happen
  }

  lazy val formAnswersWithValidInputNames: Map[String, Seq[String]] = formAnswers.filter {
    case (inputName, _) if inputName.startsWith("input") => true
    case (inputName, _) if inputName == "csrfToken"      => false
    case (inputName, _)                                  => throw new IllegalArgumentException(s"${inputName.split("-").head} is not a supported field type.")
  }

  def convertSubmittedValuesToFormFields(submittedValues: Map[String, Seq[String]]): List[FormField] = {
    val submittedValuesTrimmed: Map[String, Seq[String]] = trimValues(submittedValues)
    val excludeFields = submittedValuesTrimmed.filter(p => p._2.contains("exclude")).keys.toList

    defaultFieldValues.map { formField =>
      {
        val fieldValue: List[(String, Seq[String])] = getSubmittedFieldValue(formField.fieldId, submittedValuesTrimmed)
        if (excludeFields.contains(fieldValue.head._1)) {
          formField
        } else {
          validateFormFields(formField, fieldValue)
        }
      }
    }
  }

  private def validateFormFields(formField: FormField, fieldValue: List[(String, Seq[String])]): FormField = {
    formField match {
      case dateField: DateField =>
        val (day, month, year) = fieldValue.toDate
        DateField
          .update(dateField, day, month, year)
          .copy(fieldErrors = DateField.validate(day, month, year, dateField).map(List(_)).getOrElse(Nil))

      case radioButtonGroupField: RadioButtonGroupField =>
        val selectedOption = fieldValue.getValue(radioButtonGroupField.fieldId)
        val dependencies = radioButtonGroupField.dependencies
          .get(selectedOption)
          .map(_.map(formField => formField.fieldId -> fieldValue.getValue(s"${radioButtonGroupField.fieldId}-${formField.fieldId}-$selectedOption")).toMap)
          .getOrElse(Map.empty)
        RadioButtonGroupField
          .update(radioButtonGroupField, selectedOption, dependencies)
          .copy(fieldErrors = RadioButtonGroupField.validate(selectedOption, dependencies, radioButtonGroupField))

      case textField: TextField =>
        val text = fieldValue.head._2.head
        TextField
          .update(textField, text)
          .copy(fieldErrors = TextField.validate(text, textField).map(List(_)).getOrElse(Nil))

      case dropdownField: DropdownField =>
        val text = fieldValue.head._2.headOption.getOrElse("")
        DropdownField
          .update(dropdownField, text)
          .copy(fieldErrors = DropdownField.validate(text, dropdownField).map(List(_)).getOrElse(Nil))
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

  private def trimValues(submittedValues: Map[String, Seq[String]]): Map[String, Seq[String]] =
    submittedValues.map { case (key, values) => key -> values.map(_.trim) }

  implicit class FieldValueHelper(fieldValue: List[(String, Seq[String])]) {

    def toDate: (String, String, String) =
      (getValue("-day"), getValue("-month"), getValue("-year"))

    def getValue(key: String): String = {
      fieldValue.find(_._1.endsWith(key)).flatMap(_._2.headOption).getOrElse("")
    }
  }
}
