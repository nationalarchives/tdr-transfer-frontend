package services

import io.circe.{Decoder, Json}
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{CopyObjectRequest, GetObjectRequest, GetObjectResponse, GetObjectTaggingRequest, ListObjectsV2Request, PutObjectRequest, PutObjectResponse}
import io.circe.syntax._
import io.circe.parser.decode
import io.circe.generic.auto._
import services.S3Service._
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer}
import software.amazon.awssdk.core.internal.async.ByteArrayAsyncResponseTransformer

import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.jdk.FutureConverters._
import scala.jdk.CollectionConverters._

class S3Service @Inject()(implicit val ec: ExecutionContext) {
  val bucket = "dp-sam-test-bucket"
  val exportBucket = "dp-sam-test-export-bucket"
  val s3Client: S3AsyncClient = S3AsyncClient
    .builder
    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
    .build()

  def export(userId: UUID, consignmentId: UUID): Future[List[PutObjectResponse]] = {
    listObjects(userId, consignmentId).flatMap { res =>
      val keys = res.contents().asScala.toList.map(_.key()).filterNot(_.endsWith(".file"))
      val keyToMetadata = keys.groupBy(key => key.substring(0, key.lastIndexOf(".")))
      Future.sequence {
        keyToMetadata.map {
          case (key, metadataList) =>
            val jsonObj = metadataList.map { metadataKey =>
              metadataKey.substring(metadataKey.lastIndexOf(".") +1) -> decode[Json](getObjectAsString(metadataKey)).getOrElse(Json.obj())
            }
            val exportKey = key.split("/").tail.mkString("/")
            val request = PutObjectRequest.builder.key(s"$exportKey.metadata").bucket(exportBucket).build
            val copyRequest = CopyObjectRequest.builder.sourceBucket(bucket).sourceKey(s"$key.file").destinationBucket(exportBucket).destinationKey(s"$exportKey.file").build()

            for {
              res <- s3Client.putObject(request, AsyncRequestBody.fromString(Json.obj(jsonObj:_*).noSpaces)).asScala
              _ <- s3Client.copyObject(copyRequest).asScala
            } yield res
        }.toList
      }
    }
  }

  private def getObjectAsString(metadataKey: String): String = {
    val request = GetObjectRequest.builder.bucket(bucket).key(metadataKey).build()
    s3Client.getObject(request, AsyncResponseTransformer.toBytes[GetObjectResponse]).get().asByteArray().map(_.toChar).mkString
  }

  def countFiles(userId: UUID, consignmentId: UUID): Future[Int] = {
    listObjects(userId, consignmentId).map(_.contents().size())
  }

  private case class ClientMetadata(lastModified: String, path: String, fileId: String)

  private def decodeFromS3[T](key: String)(implicit decoder: Decoder[T]): T = {
    val jsonString = getObjectAsString(key)
    decode[T](jsonString) match {
      case Left(error) => throw error
      case Right(value) => value
    }
  }

  def saveCustomMetadata(userId: UUID, consignmentId: UUID, data: Array[Byte]): Future[List[PutObjectResponse]] = {
    val csvString = data.map(_.toChar).mkString
    val lines = csvString.split("\n")
    val headerFields = lines.head.split(",")
    listObjects(userId, consignmentId).map { res =>
      res.contents().asScala.toList.filter(_.key().endsWith(".metadata")).map { obj =>
        val clientMetadata = decodeFromS3[ClientMetadata](obj.key)
        clientMetadata.path -> clientMetadata.fileId
      }.toMap
    }.flatMap { pathToIdMap =>
      Future.traverse(lines.tail.toList) { rows =>
        val rowFields = rows.split(",")
        val filePath = rowFields.head
        val jsonFields = headerFields.zipWithIndex.map {
          case (field, idx) => mapping(field.trim) -> Json.fromString(if (rowFields.isDefinedAt(idx)) {rowFields(idx)} else {""})
        }
        val fileId = pathToIdMap(filePath)
        val request = PutObjectRequest.builder.key(s"$userId/$consignmentId/$fileId.custom").bucket(bucket).build
        s3Client.putObject(request, AsyncRequestBody.fromString(Json.obj(jsonFields:_*).noSpaces)).asScala
      }
    }
  }

  def getConsignmentFileMetadata(userId: UUID, consignmentId: UUID): Future[List[Files]] = {
    listObjects(userId, consignmentId).map { res =>
      val keys = res.contents().asScala.toList.map(_.key())
      val keysWithoutSuffix = res.contents().asScala.toList.map(o => o.key().substring(0, o.key().lastIndexOf("."))).toSet

      case class MetadataSchemaProperty(`type`: String, default: Option[String])
      case class MetadataSchema(properties: Map[String, MetadataSchemaProperty])
      keysWithoutSuffix.map { key =>
        val clientMetadata = decodeFromS3[ClientMetadata](s"$key.metadata")
        val maybeFileName = clientMetadata.path.split("/").lastOption
        val fileMetadata = if (keys.contains(s"$key.custom")) {
          decodeFromS3[Map[String, String]](s"$key.custom").map {
            case (k, v) => FileMetadata(k, v)
          }
        } else {
          val metadataSchema = decode[MetadataSchema](Source.fromResource("metadata.json").getLines().mkString) match {
            case Left(error) => throw error
            case Right(value) => value
          }
          val additionalMetadata = List(FileMetadata("ClientSideOriginalFilepath",clientMetadata.path),FileMetadata("Filename",maybeFileName.getOrElse("")), FileMetadata("ClientSideFileLastModifiedDate",clientMetadata.lastModified))
          additionalMetadata ++ metadataSchema.properties.map(property => FileMetadata(property._1, property._2.default.getOrElse("")))
        }
        Files(UUID.fromString(clientMetadata.fileId), maybeFileName, fileMetadata.toList)
      }.toList
    }
  }

  private def listObjects(userId: UUID, consignmentId: UUID) = {
    s3Client.listObjectsV2(ListObjectsV2Request.builder.bucket(bucket).prefix(s"$userId/$consignmentId").build()).asScala
  }

  def getFileChecksStatus(userId: UUID, consignmentId: UUID): Future[FileChecks] = {
    listObjects(userId, consignmentId).flatMap { res =>
      Future.traverse(res.contents().asScala.toList.filter(_.key().endsWith(".file"))) { file =>
        val request = GetObjectTaggingRequest.builder.bucket(bucket).key(file.key).build
        s3Client.getObjectTagging(request).asScala.map { tags =>
          FileTags(file.key, tags.tagSet().asScala.toList.map(t => t.key -> t.value).toMap)
        }
      }.map { fileTags =>
        FileChecks(
          fileTags.map(t => FileChecksFile(t.tags.values.toList)),
          fileTags.size,
          fileTags.count(t => t.tags.contains("GuardDutyMalwareScanStatus") && t.tags.get("GuardDutyMalwareScanStatus").contains("NO_THREATS_FOUND")),
          fileTags.count(_.tags.contains("FileFormatComplete")),
          fileTags.count(_.tags.contains("ChecksumComplete"))
        )
      }
    }
  }
}

object S3Service {
  val mapping: Map[String, String] = Map("alternate filename" -> "TitleAlternate", "is filename closed" -> "TitleClosed", "UUID" -> "UUID", "foi exemption code" -> "FoiExemptionCode", "filename" -> "Filename", "foi schedule date" -> "FoiExemptionAsserted", "is description closed" -> "DescriptionClosed", "closure period" -> "ClosurePeriod", "description" -> "description", "alternate description" -> "DescriptionAlternate", "translated filename" -> "file_name_translation", "closure start date" -> "ClosureStartDate", "date of the record" -> "end_date", "date last modified" -> "ClientSideFileLastModifiedDate", "filepath" -> "ClientSideOriginalFilepath", "language" -> "Language", "closure status" -> "ClosureType", "former reference" -> "former_reference_department")

  case class FileTags(fileId: String, tags: Map[String, String])

  case class FileChecksFile(fileStatus: List[String])

  case class FileChecks(files: List[FileChecksFile], totalFiles: Int, checksumProcessed: Int, avProcessed: Int, fileFormatProcessed: Int) {
    def allChecksSucceeded: Boolean = (checksumProcessed + avProcessed + fileFormatProcessed) / 3 == totalFiles
  }

  case class FileMetadata(name: String, value: String)

  case class FileStatuses(statusType: String, statusValue: String)

  case class Files(fileId: UUID, fileName: Option[String], fileMetadata: List[FileMetadata])

  case class GetConsignmentFileMetadata(files: List[Files], consignmentReference: String)
}
