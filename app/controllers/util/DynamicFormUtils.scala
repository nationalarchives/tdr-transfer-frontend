package controllers.util

import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment.Files.FileMetadata
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

  def validateAndConvertSubmittedValuesToFormFields(submittedValues: Map[String, Seq[String]]): List[FormField] = {
    val submittedValuesTrimmed: Map[String, Seq[String]] = trimValues(submittedValues)

    defaultFieldValues.map { formField =>
      {
        val fieldValue: List[(String, Seq[String])] = getSubmittedFieldValue(formField.fieldId, submittedValuesTrimmed)
        formField match {
          case dateField: DateField =>
            val day = fieldValue.find(_._1.endsWith("-day")).map(_._2.head).getOrElse("")
            val month = fieldValue.find(_._1.endsWith("-month")).map(_._2.head).getOrElse("")
            val year = fieldValue.find(_._1.endsWith("-year")).map(_._2.head).getOrElse("")
            DateField
              .update(dateField, day, month, year)
              .copy(fieldErrors = DateField.validate(day, month, year, dateField).map(List(_)).getOrElse(Nil))

          case radioButtonGroupField: RadioButtonGroupField =>
            val selectedOption = fieldValue.head._2.headOption.getOrElse("")
            radioButtonGroupField
              .copy(selectedOption = selectedOption)
              .copy(fieldErrors = RadioButtonGroupField.validate(selectedOption, radioButtonGroupField).map(List(_)).getOrElse(Nil))

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

          case checkboxField: CheckboxField =>
            //Updates the selectedOptions with the values of all checkboxes that are checked
            val selectedOptions = fieldValue.flatMap{fieldValue => {
              fieldValue._2.map(nameAndValue => {
                FileMetadata(nameAndValue, nameAndValue)
              })
            }}
              CheckboxField
                .update(checkboxField, selectedOptions)
                .copy(fieldErrors = CheckboxField.validate(checkboxField, selectedOptions).map(List(_)).getOrElse(Nil))
        }
      }
    }
  }

  private def getSubmittedFieldValue(fieldId: String, submittedValues: Map[String, Seq[String]]): List[(String, Seq[String])] = {
    val fieldValue = submittedValues.filter(_._1.contains(fieldId)).toList
/*    if (fieldValue.isEmpty) {
      throw new IllegalArgumentException(s"Metadata name $fieldId does not exist in submitted form values")
    } else {
      fieldValue
    }*/
    fieldValue
  }

  private def trimValues(submittedValues: Map[String, Seq[String]]): Map[String, Seq[String]] =
    submittedValues.map { case (key, values) => key -> values.map(_.trim) }
}
