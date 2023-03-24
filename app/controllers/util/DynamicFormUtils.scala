package controllers.util

import play.api.mvc.{AnyContent, Request}

class DynamicFormUtils(request: Request[AnyContent], defaultFieldValues: List[FormField]) {
  type InputName = String
  type OptionSelected = String

  private val formAnswers: Map[InputName, Seq[OptionSelected]] = request.body.asFormUrlEncoded match {
    case Some(answers: Map[InputName, Seq[OptionSelected]]) => answers
    case _                                                  => throw new Exception("Error: There were no values submitted.") // This should never happen
  }

  lazy val formAnswersWithValidInputNames: Map[InputName, Seq[OptionSelected]] = formAnswers.filter {
    case (inputName, _) if inputName.startsWith("input")          => true
    case (inputName, _) if inputName == "csrfToken"               => false
    case (inputName, _) if inputName == "tna-multi-select-search" => false
    case (inputName, _) if inputName == "details"                 => false
    case (inputName, _)                                           => throw new IllegalArgumentException(s"${inputName.split("-").head} is not a supported field type.")
  }

  def convertSubmittedValuesToFormFields(submittedValues: Map[InputName, Seq[OptionSelected]]): List[FormField] = {
    val submittedValuesTrimmed: List[SubmittedValue] = convertToSubmittedValues(submittedValues)
    val excludeFields: List[String] = submittedValuesTrimmed.collect {
      case submittedValue if submittedValue.optionsSelected.contains("exclude") => submittedValue.inputName
    }

    defaultFieldValues.map { formField =>
      val matchingFieldValue: List[SubmittedValue] = getSubmittedFieldValue(formField.fieldId, submittedValuesTrimmed)
      if (excludeFields.contains(matchingFieldValue.head.inputName)) {
        formField
      } else {
        validateFormFields(formField, matchingFieldValue)
      }
    }
  }

  private def validateFormFields(formField: FormField, fieldValue: List[SubmittedValue]): FormField = {
    val optionsSelected: Seq[InputName] = fieldValue.head.optionsSelected

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
        val text = optionsSelected.head
        TextField
          .update(textField, text)
          .copy(fieldErrors = TextField.validate(text, textField).map(List(_)).getOrElse(Nil))

      case textAreaField: TextAreaField =>
        val text = optionsSelected.head
        TextAreaField
          .update(textAreaField, text)
          .copy(fieldErrors = TextAreaField.validate(text, textAreaField).map(List(_)).getOrElse(Nil))

      case dropdownField: DropdownField =>
        val selectedValue = optionsSelected.headOption
        DropdownField
          .update(dropdownField, selectedValue)
          .copy(fieldErrors = DropdownField.validate(selectedValue, dropdownField).map(List(_)).getOrElse(Nil))

      case multiSelectField: MultiSelectField =>
        val selectedValues = optionsSelected
        MultiSelectField
          .update(multiSelectField, selectedValues)
          .copy(fieldErrors = MultiSelectField.validate(selectedValues, multiSelectField).map(List(_)).getOrElse(Nil))
    }
  }

  private def getSubmittedFieldValue(fieldId: String, submittedValues: List[SubmittedValue]): List[SubmittedValue] = {
    val fieldValue: List[SubmittedValue] = submittedValues.filter(_.inputName.contains(fieldId))
    if (fieldValue.isEmpty) List(SubmittedValue(fieldId, Nil)) else fieldValue
  }

  private def convertToSubmittedValues(submittedValues: Map[InputName, Seq[OptionSelected]]): List[SubmittedValue] =
    submittedValues.map { case (key, values) => SubmittedValue(key, values.map(_.trim)) }.toList

  implicit class FieldValueHelper(fieldValue: List[SubmittedValue]) {

    def toDate: (String, String, String) =
      (getValue("-day"), getValue("-month"), getValue("-year"))

    def getValue(key: String): String = {
      val fieldWithInputNameEndingWithKey: Option[SubmittedValue] = fieldValue.find { _.inputName.endsWith(key) }
      fieldWithInputNameEndingWithKey.flatMap { _.optionsSelected.headOption }.getOrElse("")
    }
  }
}

case class SubmittedValue(inputName: String, optionsSelected: Seq[String])
