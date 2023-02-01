package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.CurrentStatus
import org.mockito.Mockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers._
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}
import configuration.GraphQLBackend._
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.wordspec.AnyWordSpec

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

  override def afterEach(): Unit = {
    Mockito.reset(getConsignmentStatusClient)
  }

  "consignmentStatus function" should {
    "return the consignment status" in {
      val data = Option(
        gcs.Data(
          Option(
            gcs.GetConsignment(
              None,
              CurrentStatus(
                Option("TestStatus1"),
                Option("TestStatus2"),
                Option("TestStatus3"),
                Option("TestStatus4"),
                Option("TestStatus5"),
                Option("TestStatus6")
              )
            )
          )
        )
      )
      val response = GraphQlResponse(data, Nil)
      when(getConsignmentStatusClient.getResult(token, gcs.document, Some(gcs.Variables(consignmentId))))
        .thenReturn(Future.successful(response))
      val status = consignmentStatusService.getConsignmentStatus(consignmentId, token).futureValue
      status should equal(
        Option(
          CurrentStatus(
            Option("TestStatus1"),
            Option("TestStatus2"),
            Option("TestStatus3"),
            Option("TestStatus4"),
            Option("TestStatus5"),
            Option("TestStatus6")
          )
        )
      )
    }

    "return no consignment status if the consignment doesn't exist" in {
      val getConsignmentData: Option[gcs.Data] = Option(gcs.Data(Option.empty))
      val response = GraphQlResponse(getConsignmentData, Nil)
      when(getConsignmentStatusClient.getResult(token, gcs.document, Some(gcs.Variables(consignmentId))))
        .thenReturn(Future.successful(response))
      val status = consignmentStatusService.getConsignmentStatus(consignmentId, token).futureValue
      status should equal(Option.empty)
    }
  }
}
