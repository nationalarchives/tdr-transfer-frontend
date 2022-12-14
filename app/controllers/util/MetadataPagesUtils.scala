package controllers.util

import graphql.codegen.types.{FileFilters, FileMetadataFilters}

import java.util.UUID

object MetadataPagesUtils {

  def getFileFilters(metadataType: String, fileIds: List[UUID], filterByMetadataType: Boolean = false): Option[FileFilters] = {
    val metadataTypeFilter: Option[FileMetadataFilters] =
      if (filterByMetadataType) {
        metadataType match {
          case "closure"     => Some(FileMetadataFilters(Some(true), None))
          case "descriptive" => Some(FileMetadataFilters(None, Some(true)))
          case _             => throw new IllegalArgumentException(s"Invalid metadata type: $metadataType")
        }
      } else {
        None
      }
    Option(FileFilters(Option("File"), Option(fileIds), None, metadataTypeFilter))
  }
}
