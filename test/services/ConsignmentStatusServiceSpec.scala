package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.ConsignmentStatuses
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import services.ConsignmentStatusService.{Series, TransferAgreement}
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.time.{LocalDateTime, ZoneId, ZonedDateTime}
import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class ConsignmentStatusServiceSpec extends AnyWordSpec with MockitoSugar with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQLConfig = mock[GraphQLConfiguration]
  private val getConsignmentStatusClient = mock[GraphQLClient[gcs.Data, gcs.Variables]]
  private val token = new BearerAccessToken("some-token")
  private val consignmentId = UUID.fromString("e1ca3948-ee41-4e80-85e6-2123040c135d")
  when(graphQLConfig.getClient[gcs.Data, gcs.Variables]()).thenReturn(getConsignmentStatusClient)

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

  "StatusTypes" should {
    "have the correct ids" in {
      ConsignmentStatusService.Series.id should equal("Series")
      ConsignmentStatusService.Upload.id should equal("Upload")
      ConsignmentStatusService.TransferAgreement.id should equal("TransferAgreement")
      ConsignmentStatusService.Export.id should equal("Export")
    }
  }

  "getStatusValue" should {
    "return the values for the given status types from a list of statuses" in {
      val result = consignmentStatusService.getStatusValue(statuses, Set(Series, TransferAgreement))

      result.size shouldBe 2
      result.get(Series).flatten should equal(Some("Completed"))
      result.get(TransferAgreement).flatten should equal(Some("Completed"))
    }

    "return 'None' for value where status type is not present in list of statuses" in {
      val result = consignmentStatusService.getStatusValue(List(), Set(Series, TransferAgreement))

      result.size shouldBe 2
      result.get(Series).flatten shouldBe None
      result.get(TransferAgreement).flatten shouldBe None
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
}
