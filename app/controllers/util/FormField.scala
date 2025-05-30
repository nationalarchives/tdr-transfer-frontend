package controllers.util

abstract class FormField {
  val fieldId: String
  val fieldName: String
  val fieldAlternativeName: String
  val fieldDescription: String
  val fieldInsetTexts: List[String]
  val multiValue: Boolean
  val isRequired: Boolean
  val fieldErrors: List[String]
  val dependencies: Map[String, List[FormField]] = Map.empty
  def selectedOptionNames(): List[String]
  def selectedOptions(): String

}

case class InputNameAndValue(name: String, value: String, placeHolder: String = "")

case class DropdownField(
    fieldId: String,
    fieldName: String,
    fieldAlternativeName: String,
    fieldDescription: String,
    fieldInsetTexts: List[String],
    multiValue: Boolean,
    options: Seq[InputNameAndValue],
    selectedOption: Option[InputNameAndValue],
    isRequired: Boolean,
    fieldErrors: List[String] = Nil,
    override val dependencies: Map[String, List[FormField]] = Map.empty
) extends FormField {
  override def selectedOptionNames(): List[String] = selectedOption.map(_.name).toList
  override def selectedOptions(): String = selectedOption.map(_.value).getOrElse("")
}

case class Details(detailsSummary: String, detailsText: String)
