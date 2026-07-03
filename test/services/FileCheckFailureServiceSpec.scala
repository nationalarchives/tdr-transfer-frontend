package services

import configuration.ApplicationConfig
import io.circe.generic.auto._
import io.circe.parser.decode
import org.mockito.ArgumentMatchers.{anyInt, anyString}
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import software.amazon.awssdk.services.s3.model.S3Object
import uk.gov.nationalarchives.aws.utils.s3.S3Utils

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class FileCheckFailureServiceSpec extends AnyWordSpec with MockitoSugar {

  val consignmentId: UUID = UUID.fromString("7bad7c2d-597d-431d-b1bb-ebcd1d9840c8")
  val fileId1: String = "183b14b1-e0aa-45fd-bc01-8073afeb8518"
  val fileId2: String = "294c25c2-f1bb-56ge-c2cc-fcde2cba9629"
  val testBucket = "tdr-transfer-errors-intg"

  private def mockAppConfig: ApplicationConfig = {
    val appConfig = mock[ApplicationConfig]
    when(appConfig.transferErrorsS3BucketName).thenReturn(testBucket)
    appConfig
  }

  private def s3ObjectWithKey(key: String): S3Object =
    S3Object.builder().key(key).build()

  private def fileCheckErrorWith(originalPath: String, statuses: List[FileCheckStatus]): FileCheckError =
    FileCheckError(
      file = FileInfo(
        originalPath = originalPath,
        fileCheckResults = FileCheckResults(
          fileFormat = List(
            FileFormatResult(
              fileId = fileId1,
              matches = List(FileFormatMatch(Some("x-fmt/111"), Some("Plain Text File")))
            )
          )
        )
      ),
      statuses = statuses
    )

  "getFileCheckFailures" should {

    "return a list of FileCheckFailure for each object in the S3 prefix" in {
      val s3Utils = mock[S3Utils]
      val key1 = s"$consignmentId/filechecks/$fileId1"

      when(s3Utils.listAllObjectsWithPrefix(anyString, anyString, anyInt))
        .thenReturn(List(s3ObjectWithKey(key1)))

      when(s3Utils.decodeS3JsonObject[FileCheckError](anyString, anyString)(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
          fileCheckErrorWith(
            "MixedResolution/zerobyte.txt",
            List(
              FileCheckStatus(id = fileId1, statusType = "File", statusName = "FFID", statusValue = "ZeroByteFile")
            )
          )
        )

      val service = new FileCheckFailureService(mockAppConfig)
      val result = service.getFileCheckFailures(consignmentId, s3Utils).futureValue

      result.size shouldBe 1
      result.head.originalPath shouldBe "MixedResolution/zerobyte.txt"
      result.head.filename shouldBe "zerobyte.txt"
      result.head.matches shouldBe List(FileFormatMatch(Some("x-fmt/111"), Some("Plain Text File")))
    }

    "convert statuses to status actions using StatusActions.action" in {
      val s3Utils = mock[S3Utils]

      when(s3Utils.listAllObjectsWithPrefix(anyString, anyString, anyInt))
        .thenReturn(List(s3ObjectWithKey(s"$consignmentId/filechecks/$fileId1")))

      when(s3Utils.decodeS3JsonObject[FileCheckError](anyString, anyString)(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
          fileCheckErrorWith(
            "MixedResolution/zerobyte.txt",
            List(
              FileCheckStatus(id = fileId1, statusType = "File", statusName = "FFID", statusValue = "ZeroByteFile"),
              FileCheckStatus(id = fileId1, statusType = "File", statusName = "ClientChecks", statusValue = "CompletedWithIssues")
            )
          )
        )

      val service = new FileCheckFailureService(mockAppConfig)
      val result = service.getFileCheckFailures(consignmentId, s3Utils).futureValue

      result.head.statusActions shouldBe List(
        FileCheckStatusAction("File Format", "User", "Check", "This is an empty file and has no archival value. Check if you have a complete version of this file.")
      )
    }

    "exclude statuses that produce no action (e.g. Success, Completed, InProgress)" in {
      val s3Utils = mock[S3Utils]

      when(s3Utils.listAllObjectsWithPrefix(anyString, anyString, anyInt))
        .thenReturn(List(s3ObjectWithKey(s"$consignmentId/filechecks/$fileId1")))

      when(s3Utils.decodeS3JsonObject[FileCheckError](anyString, anyString)(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
          fileCheckErrorWith(
            "docs/report.pdf",
            List(
              FileCheckStatus(id = fileId1, statusType = "File", statusName = "FFID", statusValue = "Success"),
              FileCheckStatus(id = fileId1, statusType = "File", statusName = "ChecksumMatch", statusValue = "Completed")
            )
          )
        )

      val service = new FileCheckFailureService(mockAppConfig)
      val result = service.getFileCheckFailures(consignmentId, s3Utils).futureValue

      result.head.statusActions shouldBe empty
    }

    "produce a TNASupport action for non user fixable statuses" in {
      val s3Utils = mock[S3Utils]

      when(s3Utils.listAllObjectsWithPrefix(anyString, anyString, anyInt))
        .thenReturn(List(s3ObjectWithKey(s"$consignmentId/filechecks/$fileId1")))

      when(s3Utils.decodeS3JsonObject[FileCheckError](anyString, anyString)(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
          fileCheckErrorWith(
            "infected.exe",
            List(
              FileCheckStatus(id = fileId1, statusType = "File", statusName = "Antivirus", statusValue = "VirusDetected")
            )
          )
        )

      val service = new FileCheckFailureService(mockAppConfig)
      val result = service.getFileCheckFailures(consignmentId, s3Utils).futureValue

      result.head.statusActions shouldBe List(
        FileCheckStatusAction("Antivirus", "TNA", "Raise a support request", "This file cannot be processed - we will investigate this.")
      )
    }

    "ignore statuses with an unrecognised status name" in {
      val s3Utils = mock[S3Utils]

      when(s3Utils.listAllObjectsWithPrefix(anyString, anyString, anyInt))
        .thenReturn(List(s3ObjectWithKey(s"$consignmentId/filechecks/$fileId1")))

      when(s3Utils.decodeS3JsonObject[FileCheckError](anyString, anyString)(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
          fileCheckErrorWith(
            "file.txt",
            List(
              FileCheckStatus(id = fileId1, statusType = "File", statusName = "UnknownType", statusValue = "Failed"),
              FileCheckStatus(id = fileId1, statusType = "File", statusName = "FFID", statusValue = "ZeroByteFile")
            )
          )
        )

      val service = new FileCheckFailureService(mockAppConfig)
      val result = service.getFileCheckFailures(consignmentId, s3Utils).futureValue

      result.head.statusActions shouldBe List(
        FileCheckStatusAction("File Format", "User", "Check", "This is an empty file and has no archival value. Check if you have a complete version of this file.")
      )
    }

    "return an empty list when there are no objects in the S3 prefix" in {
      val s3Utils = mock[S3Utils]

      when(s3Utils.listAllObjectsWithPrefix(anyString, anyString, anyInt))
        .thenReturn(List.empty)

      val service = new FileCheckFailureService(mockAppConfig)
      val result = service.getFileCheckFailures(consignmentId, s3Utils).futureValue

      result shouldBe empty
    }

    "return failures for multiple S3 objects" in {
      val s3Utils = mock[S3Utils]
      val key1 = s"$consignmentId/filechecks/$fileId1"
      val key2 = s"$consignmentId/filechecks/$fileId2"

      when(s3Utils.listAllObjectsWithPrefix(anyString, anyString, anyInt))
        .thenReturn(List(s3ObjectWithKey(key1), s3ObjectWithKey(key2)))

      when(s3Utils.decodeS3JsonObject[FileCheckError](anyString, org.mockito.ArgumentMatchers.eq(key1))(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
          fileCheckErrorWith(
            "file1.txt",
            List(
              FileCheckStatus(fileId1, "File", "FFID", "ZeroByteFile")
            )
          )
        )

      when(s3Utils.decodeS3JsonObject[FileCheckError](anyString, org.mockito.ArgumentMatchers.eq(key2))(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
          fileCheckErrorWith(
            "file2.pdf",
            List(
              FileCheckStatus(fileId2, "File", "Antivirus", "VirusDetected")
            )
          )
        )

      val service = new FileCheckFailureService(mockAppConfig)
      val result = service.getFileCheckFailures(consignmentId, s3Utils).futureValue

      result.size shouldBe 2
      result.map(_.originalPath) shouldBe List("file1.txt", "file2.pdf")
      result.head.statusActions shouldBe List(
        FileCheckStatusAction("File Format", "User", "Check", "This is an empty file and has no archival value. Check if you have a complete version of this file.")
      )
      result(1).statusActions shouldBe List(
        FileCheckStatusAction("Antivirus", "TNA", "Raise a support request", "This file cannot be processed - we will investigate this.")
      )
    }

    "use the correct bucket and prefix when listing S3 objects" in {
      val s3Utils = mock[S3Utils]

      val bucketCaptor = org.mockito.ArgumentCaptor.forClass(classOf[String])
      val prefixCaptor = org.mockito.ArgumentCaptor.forClass(classOf[String])

      when(s3Utils.listAllObjectsWithPrefix(bucketCaptor.capture(), prefixCaptor.capture(), anyInt))
        .thenReturn(List.empty)

      val service = new FileCheckFailureService(mockAppConfig)
      service.getFileCheckFailures(consignmentId, s3Utils).futureValue

      bucketCaptor.getValue shouldBe testBucket
      prefixCaptor.getValue shouldBe s"$consignmentId/filechecks/"
    }

    "process more objects than the parallel batch size correctly" in {
      val s3Utils = mock[S3Utils]
      val objectCount = 12
      val keys = (1 to objectCount).map(i => s"$consignmentId/filechecks/file-$i").toList

      when(s3Utils.listAllObjectsWithPrefix(anyString, anyString, anyInt))
        .thenReturn(keys.map(s3ObjectWithKey))

      keys.zipWithIndex.foreach { case (key, i) =>
        when(s3Utils.decodeS3JsonObject[FileCheckError](anyString, org.mockito.ArgumentMatchers.eq(key))(org.mockito.ArgumentMatchers.any()))
          .thenReturn(
            fileCheckErrorWith(
              s"folder/file-${i + 1}.txt",
              List(FileCheckStatus(s"id-$i", "File", "FFID", "ZeroByteFile"))
            )
          )
      }

      val service = new FileCheckFailureService(mockAppConfig)
      val result = service.getFileCheckFailures(consignmentId, s3Utils).futureValue

      result.size shouldBe objectCount
      result.map(_.filename) shouldBe (1 to objectCount).map(i => s"file-$i.txt").toList
      result.foreach(_.statusActions should not be empty)
    }

    "decode a file format match with a null puid as None instead of failing" in {
      val json =
        s"""
           |{
           |  "file": {
           |    "originalPath": "folder/file.txt",
           |    "fileCheckResults": {
           |      "fileFormat": [
           |        {
           |          "fileId": "$fileId1",
           |          "matches": [
           |            { "puid": null, "formatName": "Plain Text File" }
           |          ]
           |        }
           |      ]
           |    }
           |  },
           |  "statuses": []
           |}
           |""".stripMargin

      val result = decode[FileCheckError](json)

      result.isRight shouldBe true
      val matches = result.toOption.get.file.fileCheckResults.fileFormat.head.matches
      matches shouldBe List(FileFormatMatch(None, Some("Plain Text File")))
    }
  }
}
