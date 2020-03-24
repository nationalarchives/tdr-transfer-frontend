package services

import java.util.UUID

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{okJson, post, urlEqualTo, _}
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignment.{getConsignment => gc}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.scalatest.Matchers._
import util.FrontEndTestHelper
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.time.{Millis, Seconds, Span}
import sttp.client.HttpError
import uk.gov.nationalarchives.tdr.GraphQLClient

import scala.concurrent.ExecutionContext

class GetConsignmentServiceSpec extends FrontEndTestHelper {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "GetConsignmentService GET" should {

    "Return true when given a valid consignment id" in {
      val graphQLConfig = new GraphQLConfiguration(app.configuration)
      val graphQLClient = graphQLConfig.getClient[gc.Data, gc.Variables]()

      val consignmentResponse: gc.GetConsignment = new gc.GetConsignment(UUID.randomUUID(), 555L)
      val data: graphQLClient.GraphqlData = graphQLClient.GraphqlData(Some(gc.Data(Some(consignmentResponse))), List())
      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(dataString)))

      val getConsignment = new GetConsignmentService(graphQLConfig).consignmentExists(999L, new BearerAccessToken("someAccessToken"))
      val actualResults = getConsignment.futureValue
      actualResults shouldBe true
    }

    "Return false if consignment with given id does not exist" in {
      val graphQLConfig = new GraphQLConfiguration(app.configuration)
      val graphQLClient = graphQLConfig.getClient[gc.Data, gc.Variables]()
      val data: graphQLClient.GraphqlData = graphQLClient.GraphqlData(Option.empty, List(GraphQLClient.GraphqlError("Error", Nil, Nil)))

      val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
      wiremockServer.stubFor(post(urlEqualTo("/graphql"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(dataString)))

      val getConsignment = new GetConsignmentService(graphQLConfig).consignmentExists(999L, new BearerAccessToken("someAccessToken"))
      val actualResults = getConsignment.futureValue
      actualResults shouldBe false
    }

    "Return an error when the API has an error" in {
      val graphQLConfig = new GraphQLConfiguration(app.configuration)
      wiremockServer.stubFor(post(urlEqualTo("/graphql")).willReturn(serverError))

      val getConsignment = new GetConsignmentService(graphQLConfig).consignmentExists(999L, new BearerAccessToken("someAccessToken"))
      val actualError = getConsignment.failed.futureValue
      actualError shouldBe a[HttpError]
    }
  }
}
