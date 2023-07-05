package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.AddConsignmentStatus.addConsignmentStatus.Variables
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import graphql.codegen.AddConsignmentStatus.{addConsignmentStatus => acs}
import graphql.codegen.types.ConsignmentStatusInput
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import services.Statuses.{InProgressValue, SeriesType, TransferAgreementType}
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentStatusServiceSpec extends AnyWordSpec with MockitoSugar with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQLConfig = mock[GraphQLConfiguration]
  private val getConsignmentStatusClient = mock[GraphQLClient[gcs.Data, gcs.Variables]]
  private val addConsignmentStatusClient = mock[GraphQLClient[acs.Data, acs.Variables]]
  private val token = new BearerAccessToken("some-token")
  private val consignmentId = UUID.fromString("e1ca3948-ee41-4e80-85e6-2123040c135d")
  private val consignmentStatusId = UUID.randomUUID()
  when(graphQLConfig.getClient[gcs.Data, gcs.Variables]()).thenReturn(getConsignmentStatusClient)
  when(graphQLConfig.getClient[acs.Data, acs.Variables]()).thenReturn(addConsignmentStatusClient)

  private val consignmentStatusService = new ConsignmentStatusService(graphQLConfig)
  private val someDateTime = ZonedDateTime.of(LocalDateTime.of(2022, 3, 10, 1, 0), ZoneId.systemDefault())
  private val statusId1 = UUID.randomUUID()
  private val statusId2 = UUID.randomUUID()
  private val statusId3 = UUID.randomUUID()
  private val statusId4 = UUID.randomUUID()
  val statuses: List[ConsignmentStatuses] = List(
    ConsignmentStatuses(statusId1, consignmentId, "Series", "Completed", someDateTime, None),
    ConsignmentStatuses(statusId2, consignmentId, "Upload", "Completed", someDateTime, None),
    ConsignmentStatuses(statusId3, consignmentId, "TransferAgreement", "Completed", someDateTime, None),
    ConsignmentStatuses(statusId4, consignmentId, "Export", "Completed", someDateTime, None)
  )

  override def afterEach(): Unit = {
    Mockito.reset(getConsignmentStatusClient)
  }

  "getStatusValues" should {
    "return the values for the given status types from a list of statuses" in {
      val result = consignmentStatusService.getStatusValues(statuses, SeriesType, TransferAgreementType)

      result.size shouldBe 2
      result.get(SeriesType).flatten should equal(Some("Completed"))
      result.get(TransferAgreementType).flatten should equal(Some("Completed"))
    }

    "return 'None' for value where status type is not present in list of statuses" in {
      val result = consignmentStatusService.getStatusValues(List(), SeriesType, TransferAgreementType)

      result.size shouldBe 2
      result.get(SeriesType).flatten shouldBe None
      result.get(TransferAgreementType).flatten shouldBe None
    }

    "return an empty map where no status types passed in" in {
      val result = consignmentStatusService.getStatusValues(statuses)

      result.size shouldBe 0
    }
  }

  "getConsignmentStatuses" should {
    "return all consignment statuses" in {
      val data = Option(
        gcs.Data(
          Option(
            gcs.GetConsignment(
              None,
              statuses
            )
          )
        )
      )

      val response = GraphQlResponse(data, Nil)
      when(getConsignmentStatusClient.getResult(token, gcs.document, Some(gcs.Variables(consignmentId))))
        .thenReturn(Future.successful(response))
      val result = consignmentStatusService.getConsignmentStatuses(consignmentId, token).futureValue

      result.size shouldBe 4
      val seriesStatus = result.find(_.statusType == "Series").get
      seriesStatus.value should equal("Completed")
      seriesStatus.consignmentId should equal(consignmentId)
      seriesStatus.consignmentStatusId should equal(statusId1)
      seriesStatus.createdDatetime should equal(someDateTime)

      val uploadStatus = result.find(_.statusType == "Upload").get
      uploadStatus.value should equal("Completed")
      uploadStatus.consignmentId should equal(consignmentId)
      uploadStatus.consignmentStatusId should equal(statusId2)
      uploadStatus.createdDatetime should equal(someDateTime)

      val transferAgreementStatus = result.find(_.statusType == "TransferAgreement").get
      transferAgreementStatus.value should equal("Completed")
      transferAgreementStatus.consignmentId should equal(consignmentId)
      transferAgreementStatus.consignmentStatusId should equal(statusId3)
      transferAgreementStatus.createdDatetime should equal(someDateTime)

      val exportStatus = result.find(_.statusType == "Export").get
      exportStatus.value should equal("Completed")
      exportStatus.consignmentId should equal(consignmentId)
      exportStatus.consignmentStatusId should equal(statusId4)
      exportStatus.createdDatetime should equal(someDateTime)
    }

    "return an empty list where no consignment statuses" in {
      val getConsignmentData: Option[gcs.Data] = Option(gcs.Data(Option.empty))
      val response = GraphQlResponse(getConsignmentData, Nil)
      when(getConsignmentStatusClient.getResult(token, gcs.document, Some(gcs.Variables(consignmentId))))
        .thenReturn(Future.successful(response))
      val statuses = consignmentStatusService.getConsignmentStatuses(consignmentId, token).futureValue
      statuses.size shouldBe 0
    }
  }

  "addConsignmentStatus" should {
    "correctly add status value for a given status type" in {
      val data: Option[acs.Data] =
        Option(acs.Data(acs.AddConsignmentStatus(consignmentStatusId, consignmentId, SeriesType.id, InProgressValue.value, ZonedDateTime.now(), None)))
      val response = GraphQlResponse(data, Nil)
      when(addConsignmentStatusClient.getResult(token, acs.document, Some(Variables(ConsignmentStatusInput(consignmentId, SeriesType.id, Some(InProgressValue.value))))))
        .thenReturn(Future.successful(response))
      val result = consignmentStatusService.addConsignmentStatus(consignmentId, SeriesType.id, InProgressValue.value, token).futureValue

      result.consignmentId should equal(consignmentId)
      result.consignmentStatusId should equal(consignmentStatusId)
      result.statusType should equal(SeriesType.id)
      result.value should equal(InProgressValue.value)
    }
  }
}
