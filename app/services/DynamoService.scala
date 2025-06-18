package services

import services.DynamoService._
import software.amazon.awssdk.services.dynamodb.{DynamoDbAsyncClient, DynamoDbClient}
import software.amazon.awssdk.services.dynamodb.model.{AttributeValue, AttributeValueUpdate, GetItemRequest, PutItemRequest, PutItemResponse, QueryRequest, UpdateItemRequest, UpdateItemResponse}
import services.ConsignmentReference._
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider

import java.time.{Instant, Year}
import java.time.temporal.ChronoField
import java.util
import scala.jdk.CollectionConverters._
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.FutureConverters._
import scala.util.Random


class DynamoService @Inject()(implicit val ec: ExecutionContext) {
  private val dynamoClient: DynamoDbAsyncClient = DynamoDbAsyncClient
    .builder
    .credentialsProvider(EnvironmentVariableCredentialsProvider.create())
    .build()
  private val tableName: String = "tdr"

  def key(consignmentId: UUID): util.Map[String, AttributeValue] = Map("consignmentId" -> toAv(consignmentId.toString)).asJava

  private def itemOpt(item: Map[String, AttributeValue], key: String): Option[String] = item.get(key).map(_.s())


  private def toAv[T](s: T): AttributeValue = s match {
    case b: Boolean => AttributeValue.builder.bool(b).build
    case o => AttributeValue.builder.s(o.toString).build
  }

  def getConsignment(consignmentId: UUID): Future[GetConsignment] = {
    dynamoClient.getItem(GetItemRequest.builder.tableName(tableName).key(key(consignmentId)).build).asScala.map { response =>
      val item: Map[String, AttributeValue] = response.item().asScala.toMap
      consignmentFromItem(item)
    }
  }

  private def consignmentFromItem(item: Map[String, AttributeValue]) = {
    val userId = UUID.fromString(item("userId").s())
    val seriesId = itemOpt(item, "series")
    val consignmentId = UUID.fromString(item("consignmentId").s())
    val transferringBodyName = item("transferringBodyName").s()
    val parentFolder = itemOpt(item, "parentFolder")
    val consignmentReference = item("consignmentReference").s()
    val parentFolderId = itemOpt(item, "parentFolderId").map(UUID.fromString)
    val fileCount = itemOpt(item, "fileCount").map(_.toInt)
    val clientSideDraftMetadataFileName = itemOpt(item, "clientSideDraftMetadataFileName")
    val consignmentType = item("consignmentType").s()
    val consignmentStatuses = item.view.filterKeys(_.startsWith("status")).map {
      case (key, av) => ConsignmentStatuses(key.replace("status_", ""), av.s())
    }.toList
    GetConsignment(consignmentId, transferringBodyName, userId, seriesId, parentFolder, consignmentReference, parentFolderId, clientSideDraftMetadataFileName, consignmentStatuses, consignmentType, fileCount)
  }

  private def seriesFromItem(item: Map[String, AttributeValue]) = {
    val id = UUID.fromString(item("id").s())
    val code = item("code").s()
    val name = item("name").s()
    Series(id, code, name)
  }

  def getConsignments(toFilter: String, indexName: String): Future[List[GetConsignment]] = {
    query(toFilter, indexName, tableName).map(av => av.map(consignmentFromItem))
  }

  def getSeries(body: String): Future[List[Series]] = query(body, "body", "tdr_series").map(a => a.map(seriesFromItem))

  def query(toFilter: String, indexName: String, tableName: String): Future[List[Map[String, AttributeValue]]] = {
    val request = QueryRequest.builder()
      .tableName(tableName)
      .indexName(s"$indexName-index")
      .keyConditionExpression(s"$indexName = :uid")
      .expressionAttributeValues(Map(
        ":uid" -> AttributeValue.builder().s(toFilter).build()
      ).asJava)
      .build()
    dynamoClient.query(request).asScala.map(res => res.items().asScala.toList.map(_.asScala.toMap))
  }

  def createConsignment(userId: UUID, transferringBody: String, series: Option[UUID], consignmentType: String): Future[AddConsignment] = {
    val consignmentReference = createConsignmentReference(Year.now().getValue, Random.between(1, 101))
    val consignmentId = UUID.randomUUID
    val item = (Map(
      "transferringBodyName" -> toAv(transferringBody),
      "consignmentId" -> toAv(consignmentId),
      "userId" -> toAv(userId),
      "consignmentType" -> toAv(consignmentType),
      "consignmentReference" -> toAv(consignmentReference)
    ) ++ series.map(s => Map("series" -> toAv(s))).getOrElse(Map())).asJava
    val request: PutItemRequest = PutItemRequest.builder.tableName(tableName).item(item).build()
    dynamoClient.putItem(request).asScala.map { _ =>
      AddConsignment(consignmentId, series, consignmentReference)
    }
  }

  def setFieldToValue[T](consignmentId: UUID, fieldName: String, value: T): Future[UpdateItemResponse] = {
    val update = AttributeValueUpdate.builder.value(toAv(value)).build()
    val request = UpdateItemRequest.builder
      .key(key(consignmentId))
      .attributeUpdates(Map(fieldName -> update).asJava)
      .tableName(tableName).build()
    dynamoClient.updateItem(request).asScala
  }
}

object DynamoService {
  case class Series(id: UUID, code: String, name: String)
  case class AddConsignment(consignmentId: UUID, seriesId: Option[UUID], consignmentReference: String)
  case class ConsignmentStatuses(statusType: String, value: String)

  case class GetConsignment(consignmentId: UUID, transferringBodyName: String, userId: UUID, seriesName: Option[String], parentFolder: Option[String], consignmentReference: String, parentFolderId: Option[UUID], clientSideDraftMetadataFileName: Option[String], consignmentStatuses: List[ConsignmentStatuses], consignmentType: String, fileCount: Option[Int])
}
