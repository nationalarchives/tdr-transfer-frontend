package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend.backend
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetCustomMetadata.{customMetadata => cm}
import graphql.codegen.types.DataType.Text
import graphql.codegen.types.PropertyType.Defined
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

class CustomMetadataServiceSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQLConfig = mock[GraphQLConfiguration]
  private val customMetadataClient = mock[GraphQLClient[cm.Data, cm.Variables]]
  private val token = new BearerAccessToken("some-token")
  private val consignmentId = UUID.fromString("e1ca3948-ee41-4e80-85e6-2123040c135d")
  when(graphQLConfig.getClient[cm.Data, cm.Variables]()).thenReturn(customMetadataClient)

  private val customMetadataService = new CustomMetadataService(graphQLConfig)

  override def afterEach(): Unit = {
    Mockito.reset(customMetadataClient)
  }

  "customMetadata" should "return the all closure metadata" in {
    val data: Option[cm.Data] = Some(
      cm.Data(
        List(
          cm.CustomMetadata(
            "TestProperty",
            Some("It's the Test Property"),
            Some("Test Property"),
            Defined,
            Some("Test Property Group"),
            Text,
            editable = false,
            multiValue = false,
            Some("TestValue"),
            1,
            List(
              cm.CustomMetadata.Values(
                "TestValue",
                List(cm.CustomMetadata.Values.Dependencies("TestDependency"))
              )
            )
          ),
          cm.CustomMetadata(
            "TestDependency",
            Some("It's the Test Dependency"),
            Some("Test Dependency"),
            Defined,
            Some("Test Dependency Group"),
            Text,
            editable = false,
            multiValue = false,
            Some("TestDependencyValue"),
            2,
            List()
          )
        )
      )
    )
    val response = GraphQlResponse(data, Nil)
    when(customMetadataClient.getResult(token, cm.document, Some(cm.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val status: List[cm.CustomMetadata] = customMetadataService.getCustomMetadata(consignmentId, token).futureValue
    status should equal(
      List(
        cm.CustomMetadata(
          "TestProperty",
          Some("It's the Test Property"),
          Some("Test Property"),
          Defined,
          Some("Test Property Group"),
          Text,
          editable = false,
          multiValue = false,
          Some("TestValue"),
          1,
          List(
            cm.CustomMetadata.Values(
              "TestValue",
              List(cm.CustomMetadata.Values.Dependencies("TestDependency"))
            )
          )
        ),
        cm.CustomMetadata(
          "TestDependency",
          Some("It's the Test Dependency"),
          Some("Test Dependency"),
          Defined,
          Some("Test Dependency Group"),
          Text,
          editable = false,
          multiValue = false,
          Some("TestDependencyValue"),
          2,
          List()
        )
      )
    )
  }

  "customMetadata" should "return an error if the API call fails" in {
    when(customMetadataClient.getResult(token, cm.document, Some(cm.Variables(consignmentId))))
      .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

    customMetadataService.getCustomMetadata(consignmentId, token).failed.futureValue shouldBe a[HttpError]
  }

  "customMetadata" should "throw an AuthorisationException if the API returns an auth error" in {
    val response = GraphQlResponse[cm.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
    when(customMetadataClient.getResult(token, cm.document, Some(cm.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val results = customMetadataService.getCustomMetadata(consignmentId, token).failed
      .futureValue.asInstanceOf[AuthorisationException]

    results shouldBe a[AuthorisationException]
  }
}
