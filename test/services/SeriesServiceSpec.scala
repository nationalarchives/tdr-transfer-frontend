package services

import java.util.UUID

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetSeries.getSeries
import graphql.codegen.GetSeries.getSeries.GetSeries
import org.keycloak.representations.AccessToken
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import org.scalatestplus.mockito.MockitoSugar
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{HttpError, NothingT, SttpBackend}
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.keycloak.Token
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import scala.concurrent.{ExecutionContext, Future}

class SeriesServiceSpec extends FlatSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend()

  private val graphQlConfig = mock[GraphQLConfiguration]
  private val graphQlClient = mock[GraphQLClient[getSeries.Data, getSeries.Variables]]
  when(graphQlConfig.getClient[getSeries.Data, getSeries.Variables]()).thenReturn(graphQlClient)

  private val seriesService: SeriesService = new SeriesService(graphQlConfig)

  private val seriesId = UUID.fromString("8ba6ad4e-546c-4e41-b3ca-72de01513d20")
  private val bodyName = "some transferring body"
  private val bodyId = UUID.fromString("d3bbde54-f872-4c92-8623-59acf48a1bbd")

  private val accessToken = new AccessToken()
  accessToken.setOtherClaims("body", bodyName)
  private val token = Token(accessToken, new BearerAccessToken("some-token"))

  override def afterEach(): Unit = {
    Mockito.reset(graphQlClient)
  }

  "getSeriesForUser" should "get the series from the API" in {
    val seriesResponse = GetSeries(seriesId, bodyId, Some("some series name"), None, None)
    val graphQlResponse = GraphQlResponse(Some(getSeries.Data(List(seriesResponse))), Nil)
    when(graphQlClient.getResult(token.bearerAccessToken, getSeries.document, Some(getSeries.Variables(bodyName))))
      .thenReturn(Future.successful(graphQlResponse))

    val series = seriesService.getSeriesForUser(token).futureValue

    series.size should equal(1)
    series.head.seriesid should equal(seriesId)
  }

  "getSeriesForUser" should "return an error if the API call fails" in {
    when(graphQlClient.getResult(token.bearerAccessToken, getSeries.document, Some(getSeries.Variables(bodyName))))
      .thenReturn(Future.failed(new HttpError("something went wrong", StatusCode.InternalServerError)))

    seriesService.getSeriesForUser(token).failed.futureValue shouldBe a[HttpError]
  }

  "getSeriesForUser" should "throw an AuthorisationException if the API returns an auth error" in {
    val response = GraphQlResponse[getSeries.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
    when(graphQlClient.getResult(token.bearerAccessToken, getSeries.document, Some(getSeries.Variables(bodyName))))
      .thenReturn(Future.successful(response))

    val results = seriesService.getSeriesForUser(token).failed.futureValue
    results shouldBe a[AuthorisationException]
  }
}
