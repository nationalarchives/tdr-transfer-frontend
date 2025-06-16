package controllers.util

case class StaticMetadata(name: String, value: String)

object MetadataProperty {
  val fileUUID = "UUID"
  val fileName = "Filename"
  val clientSideOriginalFilepath = "ClientSideOriginalFilepath"
  val description = "description"
  val title = "title"
  val fileType = "FileType"
  val closureType: StaticMetadata = StaticMetadata("ClosureType", "Closed")
  val clientSideFileLastModifiedDate = "ClientSideFileLastModifiedDate"
  val end_date = "end_date"
  val language = "Language"
  val filePath = "Filepath"
}
