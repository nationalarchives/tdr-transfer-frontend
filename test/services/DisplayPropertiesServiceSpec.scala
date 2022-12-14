package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend.backend
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetDisplayProperties.{displayProperties => dp}
import graphql.codegen.types.DataType.Text
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.{a, convertToAnyShouldWrapper, equal}
import org.scalatestplus.mockito.MockitoSugar
import sttp.client.HttpError
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class DisplayPropertiesServiceSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQLConfig = mock[GraphQLConfiguration]
  private val displayPropertiesClient = mock[GraphQLClient[dp.Data, dp.Variables]]
  private val token = new BearerAccessToken("some-token")
  private val consignmentId = UUID.fromString("e1ca3948-ee41-4e80-85e6-2123040c135d")

  when(graphQLConfig.getClient[dp.Data, dp.Variables]()).thenReturn(displayPropertiesClient)

  private val displayPropertiesService = new DisplayPropertiesService(graphQLConfig)

  override def afterEach(): Unit = {
    Mockito.reset(displayPropertiesClient)
  }

  "getDisplayProperties" should "return the all the display properties" in {
    val data: Option[dp.Data] = Some(
      dp.Data(
        List(
          dp.DisplayProperties(
            "property1",
            List(
              dp.DisplayProperties.Attributes(
                "attribute1",
                Some("value1"),
                Text
              ),
              dp.DisplayProperties.Attributes(
                "attribute2",
                Some("value2"),
                Text
              )
            )
          ),
          dp.DisplayProperties(
            "property2",
            List(
              dp.DisplayProperties.Attributes(
                "attribute1",
                Some("value1"),
                Text
              ),
              dp.DisplayProperties.Attributes(
                "attribute2",
                Some("value2"),
                Text
              )
            )
          )
        )
      )
    )
    val response = GraphQlResponse(data, Nil)
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val status: List[dp.DisplayProperties] = displayPropertiesService.getDisplayProperties(consignmentId, token).futureValue
    status should equal(
      List(
        dp.DisplayProperties(
          "property1",
          List(
            dp.DisplayProperties.Attributes(
              "attribute1",
              Some("value1"),
              Text
            ),
            dp.DisplayProperties.Attributes(
              "attribute2",
              Some("value2"),
              Text
            )
          )
        ),
        dp.DisplayProperties(
          "property2",
          List(
            dp.DisplayProperties.Attributes(
              "attribute1",
              Some("value1"),
              Text
            ),
            dp.DisplayProperties.Attributes(
              "attribute2",
              Some("value2"),
              Text
            )
          )
        )
      )
    )
  }

  "getDisplayProperties" should "return an error if the API call fails" in {
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

    displayPropertiesService.getDisplayProperties(consignmentId, token).failed.futureValue shouldBe a[HttpError]
  }

  "getDisplayProperties" should "throw an AuthorisationException if the API returns an auth error" in {
    val response = GraphQlResponse[dp.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val results = displayPropertiesService.getDisplayProperties(consignmentId, token).failed.futureValue.asInstanceOf[AuthorisationException]

    results shouldBe a[AuthorisationException]
  }
}
