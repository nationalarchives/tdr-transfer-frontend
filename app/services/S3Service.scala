package services

import io.circe.Decoder.Result
import io.circe.{Decoder, Json, JsonObject}
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.{CopyObjectRequest, GetObjectRequest, GetObjectResponse, GetObjectTaggingRequest, ListObjectsV2Request, PutObjectRequest, PutObjectResponse}
import io.circe.syntax._
import io.circe.parser.decode
import io.circe.generic.auto._
import services.S3Service._
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider
import software.amazon.awssdk.core.async.{AsyncRequestBody, AsyncResponseTransformer}

import java.nio.file.Path
import java.util.UUID
import javax.inject.Inject
import scala.annotation.tailrec
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.jdk.FutureConverters._
import scala.jdk.CollectionConverters._

class S3Service @Inject()(implicit val ec: ExecutionContext) {
  implicit class ResultUtils[T](result: Result[T]) {
    def orError: T = result match {
      case Left(error) => throw error
      case Right(value) => value
    }
  }

  val bucket = "dp-sam-test-bucket"
  private val exportBucket = "dp-sam-test-export-bucket"
  val s3Client: S3AsyncClient = S3AsyncClient
    .builder
    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
    .build()

  @tailrec
  private def createPaths(path: Path, allPaths: List[Path]): List[Path] = {
    if (Option(path).isEmpty) {
      allPaths
    } else {
      createPaths(path.getParent, path :: allPaths)
    }
  }

  def exportConsignment(userId: UUID, consignmentId: UUID): Future[List[PutObjectResponse]] = for {
    allJson <- createSingleJsonObjects(userId, consignmentId)
    res <- generateAndUpdateParentIds(userId, consignmentId, allJson)
  } yield res

  private def generateAndUpdateParentIds(userId: UUID, consignmentId: UUID, allJson: List[Json]): Future[List[PutObjectResponse]] = {
    val clientMetadata = allJson.map(_.hcursor.downField("metadata").as[ClientMetadata].orError)

    val folders = clientMetadata.map(metadata => Path.of(metadata.path))
      .flatMap(p => createPaths(p.getParent, Nil)).toSet[Path]
      .map(m => m.toString -> FolderObject(consignmentId, UUID.randomUUID, "Folder", m.toString)).toMap

    val updatedMetadata = clientMetadata.map { metadata =>
      val parentId = folders(Path.of(metadata.path).getParent.toString).fileId
      metadata.copy(parentId = Option(parentId))
    }
    Future.sequence {
      allJson.map { eachJson =>
        val fileId = eachJson.hcursor.downField("metadata").downField("fileId").as[String].orError
        val newMetadata = updatedMetadata.find(_.fileId == fileId).get
        val newJson = eachJson.asObject.getOrElse(JsonObject.empty).add("metadata", newMetadata.asJson)
        val key = s"$userId/$consignmentId/$fileId.file"

        def exportKey(fileId: String) = s"${newMetadata.consignmentId}/$fileId"

        def request(fileId: String) = PutObjectRequest.builder.key(s"${exportKey(fileId)}.metadata").bucket(exportBucket).build

        val copyRequest = CopyObjectRequest.builder
          .sourceBucket(bucket)
          .sourceKey(key)
          .destinationBucket(exportBucket)
          .destinationKey(s"${exportKey(newMetadata.fileId)}.file").build()

        for {
          res <- s3Client.putObject(request(newMetadata.fileId), AsyncRequestBody.fromString(newJson.asJson.noSpaces)).asScala
          _ <- s3Client.copyObject(copyRequest).asScala
          _ <- Future.traverse(folders.values.toList)(folder => s3Client.putObject(request(folder.fileId.toString), AsyncRequestBody.fromString(Json.obj("metadata" -> folder.asJson).noSpaces)).asScala)
        } yield res
      }
    }
  }

  private def createSingleJsonObjects(userId: UUID, consignmentId: UUID) = {
    val consignmentObjects = listObjects(userId, consignmentId)
    consignmentObjects.map { res =>
      val keys = res.contents().asScala.toList.map(_.key())
        .filterNot(_.endsWith(".file"))
        .filterNot(_.endsWith(".files"))
        .filterNot(_.contains("/statuses"))
      val keyToMetadata = keys.groupBy(key => key.substring(0, key.lastIndexOf(".")))
      keyToMetadata.map {
        case (_, metadataList) =>
          val jsonObj = metadataList.map { metadataKey =>
            metadataKey.substring(metadataKey.lastIndexOf(".") + 1) -> decode[Json](getObjectAsString(metadataKey)).getOrElse(Json.obj())
          }
          Json.obj(jsonObj: _*)
      }.toList
    }
  }

  private def getObjectAsString(metadataKey: String): String = {
    val request = GetObjectRequest.builder.bucket(bucket).key(metadataKey).build()
    s3Client.getObject(request, AsyncResponseTransformer.toBytes[GetObjectResponse]).get().asByteArray().map(_.toChar).mkString
  }

  private case class ClientMetadata(lastModified: String, path: String, fileId: String, consignmentId: String, parentId: Option[UUID] = None)

  private case class FolderObject(consignmentId: UUID, fileId: UUID, `type`: String, path: String)

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
          case (field, idx) => mapping(field.trim) -> Json.fromString(if (rowFields.isDefinedAt(idx)) {
            rowFields(idx)
          } else {
            ""
          })
        }
        val fileId = pathToIdMap(filePath)
        val request = PutObjectRequest.builder.key(s"$userId/$consignmentId/$fileId.custom").bucket(bucket).build
        s3Client.putObject(request, AsyncRequestBody.fromString(Json.obj(jsonFields: _*).noSpaces)).asScala
      }
    }
  }

  def getConsignmentFileMetadata(userId: UUID, consignmentId: UUID): Future[List[Files]] = {
    listObjects(userId, consignmentId).map { res =>
      val keys = res.contents().asScala.toList.map(_.key())
      val keysWithoutSuffix = res.contents().asScala.toList
        .filterNot(_.key().endsWith(".files"))
        .filterNot(_.key().contains("/statuses/"))
        .map(o => o.key().substring(0, o.key().lastIndexOf("."))).toSet

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
          val additionalMetadata = List(FileMetadata("ClientSideOriginalFilepath", clientMetadata.path), FileMetadata("Filename", maybeFileName.getOrElse("")), FileMetadata("ClientSideFileLastModifiedDate", clientMetadata.lastModified))
          additionalMetadata ++ metadataSchema.properties.map(property => FileMetadata(property._1, property._2.default.getOrElse("")))
        }
        Files(UUID.fromString(clientMetadata.fileId), maybeFileName, fileMetadata.toList)
      }.toList
    }
  }

  private def listObjects(userId: UUID, consignmentId: UUID, listBucket: String = bucket) = {
    s3Client.listObjectsV2(ListObjectsV2Request.builder.bucket(bucket).prefix(s"$userId/$consignmentId").build()).asScala
  }

  def getFileChecksStatus(userId: UUID, consignmentId: UUID): Future[FileChecks] = {
    listObjects(userId, consignmentId).flatMap { res =>
      val totalFiles = res.contents().asScala.count(_.key().endsWith(".file"))
      s3Client.listObjectsV2(ListObjectsV2Request.builder.bucket(bucket).prefix(s"$userId/$consignmentId/statuses").build()).asScala.map { res =>
        val contents = res.contents().asScala.toList
        val grouped = contents
          .map(_.key().split("/").takeRight(2)).groupBy(_.head)
        val fileIdToStatuses: Map[String, List[String]] = grouped.view.mapValues(_.map(_.last)).toMap
        val allStatuses = fileIdToStatuses.values.toList.flatten
        val files = fileIdToStatuses.map {
          case (_, statuses) => FileChecksFile(statuses)
        }.toList
        FileChecks(
          files, totalFiles,
          allStatuses.count(_.startsWith("checksum")),
          allStatuses.count(_.startsWith("antivirus")),
          allStatuses.count(_.startsWith("file-format")),
          allStatuses.count(_.startsWith("redacted"))
        )
      }
    }
  }
}

object S3Service {
  val mapping: Map[String, String] = Map("alternate filename" -> "TitleAlternate", "is filename closed" -> "TitleClosed", "UUID" -> "UUID", "foi exemption code" -> "FoiExemptionCode", "filename" -> "Filename", "foi schedule date" -> "FoiExemptionAsserted", "is description closed" -> "DescriptionClosed", "closure period" -> "ClosurePeriod", "description" -> "description", "alternate description" -> "DescriptionAlternate", "translated filename" -> "file_name_translation", "closure start date" -> "ClosureStartDate", "date of the record" -> "end_date", "date last modified" -> "ClientSideFileLastModifiedDate", "filepath" -> "ClientSideOriginalFilepath", "language" -> "Language", "closure status" -> "ClosureType", "former reference" -> "former_reference_department")

  case class FileTags(fileId: String, tags: Map[String, String])

  case class FileChecksFile(fileStatus: List[String])

  case class FileChecks(files: List[FileChecksFile], totalFiles: Int, checksumProcessed: Int, avProcessed: Int, fileFormatProcessed: Int, redactedProcessed: Int) {
    def allChecksSucceeded: Boolean = (checksumProcessed + avProcessed + fileFormatProcessed) / 3 == totalFiles && !files.flatMap(_.fileStatus).exists(_.contains("failure"))
  }

  case class FileMetadata(name: String, value: String)

  case class FileStatuses(statusType: String, statusValue: String)

  case class Files(fileId: UUID, fileName: Option[String], fileMetadata: List[FileMetadata])

  case class GetConsignmentFileMetadata(files: List[Files], consignmentReference: String)
}
