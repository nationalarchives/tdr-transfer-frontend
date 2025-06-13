package controllers.util

case class StaticMetadata(name: String, value: String)

object MetadataProperty {
  val foiExemptionAsserted = "FoiExemptionAsserted"
  val closureStartDate = "ClosureStartDate"
  val closurePeriod = "ClosurePeriod"
  val foiExemptionCode = "FoiExemptionCode"
  val fileUUID = "UUID"
  val fileName = "Filename"
  val titleClosed = "TitleClosed"
  val descriptionClosed = "DescriptionClosed"
  val clientSideOriginalFilepath = "ClientSideOriginalFilepath"
  val descriptionPublic = "DescriptionPublic"
  val titleAlternate = "TitleAlternate"
  val descriptionAlternate = "DescriptionAlternate"
  val description = "description"
  val title = "title"
  val fileType = "FileType"
  val closureType: StaticMetadata = StaticMetadata("ClosureType", "Closed")
  val descriptiveType: StaticMetadata = StaticMetadata("DescriptiveType", "")
  val clientSideFileLastModifiedDate = "ClientSideFileLastModifiedDate"
  val end_date = "end_date"
  val former_reference = "former_reference_department"
  val language = "Language"
  val filenameTranslated = "file_name_translation"
  val filePath = "Filepath"
}
