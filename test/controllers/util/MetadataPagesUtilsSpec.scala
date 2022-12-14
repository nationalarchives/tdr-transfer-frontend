package controllers.util

import graphql.codegen.types.{FileFilters, FileMetadataFilters}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar

import java.util.UUID

class MetadataPagesUtilsSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
  private val fileId1 = UUID.randomUUID()
  private val fileId2 = UUID.randomUUID()
  private val fileIds = List(fileId1, fileId2)

  "getFileFilters" should "return FileFilters with a File fileTypeIdentifier, the correct Ids and no metadataFilter" in {
    val fileFilters = MetadataPagesUtils.getFileFilters("closure", fileIds)

    fileFilters must be(Option(FileFilters(Option("File"), Option(fileIds), None, None)))
  }

  "getFileFilters" should "return FileFilters with a File fileTypeIdentifier, the correct Ids and a 'closure' metadata filter" in {
    val fileFilters = MetadataPagesUtils.getFileFilters("closure", fileIds, filterByMetadataType = true)

    fileFilters must be(Option(FileFilters(Option("File"), Option(fileIds), None, Some(FileMetadataFilters(Some(true), None)))))
  }

  "getFileFilters" should "return FileFilters with a File fileTypeIdentifier, the correct Ids and a 'descriptive' metadata filter" in {
    val fileFilters = MetadataPagesUtils.getFileFilters("descriptive", fileIds, filterByMetadataType = true)

    fileFilters must be(Option(FileFilters(Option("File"), Option(fileIds), None, Some(FileMetadataFilters(None, Some(true))))))
  }

  "getFileFilters" should "throw a IllegalArgumentException exception if filter is requested but an invalid metadata type has been passed in" in {
    val thrownException: IllegalArgumentException =
      the[IllegalArgumentException] thrownBy MetadataPagesUtils.getFileFilters("invalidMetadataType", fileIds, filterByMetadataType = true)

    thrownException.getMessage should equal("Invalid metadata type: invalidMetadataType")
  }
}
