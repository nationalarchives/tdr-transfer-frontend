package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend.backend
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetDisplayProperties.{displayProperties => dp}
import graphql.codegen.types.DataType.{Text, Boolean, Integer}
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
              dp.DisplayProperties.Attributes("Active", Some("true"), Boolean),
              dp.DisplayProperties.Attributes("ComponentType", Some("componentType"), Text),
              dp.DisplayProperties.Attributes("Datatype", Some("text"), Text),
              dp.DisplayProperties.Attributes("Description", Some("description value"), Text),
              dp.DisplayProperties.Attributes("Name", Some("display name"), Text),
              dp.DisplayProperties.Attributes("Editable", Some("true"), Boolean),
              dp.DisplayProperties.Attributes("Group", Some("group"), Text),
              dp.DisplayProperties.Attributes("Label", Some("label"), Text),
              dp.DisplayProperties.Attributes("MultiValue", Some("false"), Boolean),
              dp.DisplayProperties.Attributes("Ordinal", Some("11"), Integer),
              dp.DisplayProperties.Attributes("PropertyType", Some("propertyType"), Text)
            )
          )
        )
      )
    )
    val response = GraphQlResponse(data, Nil)
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val properties: List[DisplayProperty] = displayPropertiesService.getDisplayProperties(consignmentId, token).futureValue
    properties.size shouldBe 1
    val property1 = properties.find(_.propertyName == "property1").get
    property1.active shouldBe true
    property1.componentType shouldBe "componentType"
    property1.dataType shouldBe Text
    property1.displayName shouldBe "display name"
    property1.description shouldBe "description value"
    property1.editable shouldBe true
    property1.group shouldBe "group"
    property1.label shouldBe "label"
    property1.multiValue shouldBe false
    property1.ordinal shouldBe 11
    property1.propertyType shouldBe "propertyType"
  }

  "getDisplayProperties" should "return default values for all optional property fields" in {
    val data: Option[dp.Data] = Some(
      dp.Data(
        List(
          dp.DisplayProperties(
            "property1",
            List(dp.DisplayProperties.Attributes("Datatype", Some("text"), Text))
          )
        )
      )
    )
    val response = GraphQlResponse(data, Nil)
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val properties: List[DisplayProperty] = displayPropertiesService.getDisplayProperties(consignmentId, token).futureValue
    properties.size shouldBe 1
    val property1 = properties.find(_.propertyName == "property1").get
    property1.active shouldBe false
    property1.componentType shouldBe ""
    property1.dataType shouldBe Text
    property1.displayName shouldBe ""
    property1.description shouldBe ""
    property1.editable shouldBe false
    property1.group shouldBe ""
    property1.label shouldBe ""
    property1.multiValue shouldBe false
    property1.ordinal shouldBe 0
    property1.propertyType shouldBe ""
  }

  "getDisplayProperties" should "return an error if property is missing a 'data type" in {}

  "getDisplayProperties" should "return an error if the property's data type is invalid" in {}

  "getDisplayProperties" should "return an error if the property's ordinal value is invalid" in {}

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
