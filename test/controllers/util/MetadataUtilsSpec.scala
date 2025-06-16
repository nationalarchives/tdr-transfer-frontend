package controllers.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MetadataUtilsSpec extends AnyWordSpec with Matchers {

  "MetadataProperty" should {
    "have correct values for predefined properties" in {
      MetadataProperty.fileUUID shouldBe "UUID"
      MetadataProperty.fileName shouldBe "Filename"
      MetadataProperty.clientSideOriginalFilepath shouldBe "ClientSideOriginalFilepath"
      MetadataProperty.description shouldBe "description"
      MetadataProperty.title shouldBe "title"
      MetadataProperty.fileType shouldBe "FileType"
      MetadataProperty.closureType shouldBe StaticMetadata("ClosureType", "Closed")
      MetadataProperty.clientSideFileLastModifiedDate shouldBe "ClientSideFileLastModifiedDate"
      MetadataProperty.end_date shouldBe "end_date"
      MetadataProperty.language shouldBe "Language"
      MetadataProperty.filePath shouldBe "Filepath"
    }
  }
}
