package services

import cats.implicits.catsSyntaxOptionId
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend.backend
import configuration.GraphQLConfiguration
import errors.AuthorisationException
import graphql.codegen.GetDisplayProperties.{displayProperties => dp}
import graphql.codegen.types.DataType.{Boolean, Integer, Text}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor2}
import org.scalatestplus.mockito.MockitoSugar
import sttp.client.HttpError
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.error.NotAuthorisedError
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class DisplayPropertiesServiceSpec extends AnyFlatSpec with MockitoSugar with BeforeAndAfterEach with TableDrivenPropertyChecks {
  implicit val ec: ExecutionContext = ExecutionContext.global

  private val graphQLConfig = mock[GraphQLConfiguration]
  private val displayPropertiesClient = mock[GraphQLClient[dp.Data, dp.Variables]]
  private val token = new BearerAccessToken("some-token")
  private val consignmentId = UUID.fromString("e1ca3948-ee41-4e80-85e6-2123040c135d")

  when(graphQLConfig.getClient[dp.Data, dp.Variables]()).thenReturn(displayPropertiesClient)

  private val displayPropertiesService = new DisplayPropertiesService(graphQLConfig)

  private val activeProperty = dp.DisplayProperties(
    "activeProperty",
    requiredAttributes() ++
      List(
        dp.DisplayProperties.Attributes("Active", Some("true"), Boolean),
        dp.DisplayProperties.Attributes("ComponentType", Some("componentType"), Text),
        dp.DisplayProperties.Attributes("Description", Some("description value"), Text),
        dp.DisplayProperties.Attributes("Name", Some("display name"), Text),
        dp.DisplayProperties.Attributes("Editable", Some("true"), Boolean),
        dp.DisplayProperties.Attributes("Group", Some("group"), Text),
        dp.DisplayProperties.Attributes("Guidance", Some("guidance"), Text),
        dp.DisplayProperties.Attributes("Label", Some("label"), Text),
        dp.DisplayProperties.Attributes("MultiValue", Some("false"), Boolean),
        dp.DisplayProperties.Attributes("Ordinal", Some("11"), Integer),
        dp.DisplayProperties.Attributes("PropertyType", Some("propertyType"), Text),
        dp.DisplayProperties.Attributes("UnitType", Some("unitType"), Text),
        dp.DisplayProperties.Attributes("DetailsSummary", Some("summary"), Text),
        dp.DisplayProperties.Attributes("DetailsText", Some("text"), Text),
        dp.DisplayProperties.Attributes("Required", Some("true"), Boolean)
      )
  )

  private val inactiveProperty = dp.DisplayProperties(
    "inactiveProperty",
    requiredAttributes() ++
      List(
        dp.DisplayProperties.Attributes("Active", Some("false"), Boolean),
        dp.DisplayProperties.Attributes("PropertyType", Some("propertyType"), Text)
      )
  )

  private val differentPropertyTypeActiveProperty = dp.DisplayProperties(
    "differentPropertyTypeProperty",
    requiredAttributes() ++
      List(
        dp.DisplayProperties.Attributes("Active", Some("true"), Boolean),
        dp.DisplayProperties.Attributes("PropertyType", Some("differentPropertyType"), Text),
        dp.DisplayProperties.Attributes("Ordinal", Some("10"), Integer)
      )
  )

  private val differentPropertyTypeInactiveProperty = dp.DisplayProperties(
    "differentPropertyTypeProperty",
    requiredAttributes() ++
      List(
        dp.DisplayProperties.Attributes("Active", Some("false"), Boolean),
        dp.DisplayProperties.Attributes("PropertyType", Some("differentPropertyType"), Text)
      )
  )

  override def afterEach(): Unit = {
    Mockito.reset(displayPropertiesClient)
  }

  "getDisplayProperties" should "return the all the active display properties for the given 'metadata type'" in {
    val data: Option[dp.Data] = Some(
      dp.Data(
        List(activeProperty, inactiveProperty, differentPropertyTypeActiveProperty, differentPropertyTypeInactiveProperty)
      )
    )
    val response = GraphQlResponse(data, Nil)
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val properties: List[DisplayProperty] = displayPropertiesService.getDisplayProperties(consignmentId, token, "propertyType".some).futureValue
    properties.size should equal(1)
    properties.head.propertyName should equal("activeProperty")
  }

  "getDisplayProperties" should "not filter by 'metadata type' and return all the sorted display properties by 'ordinal' when 'metadata type' is not passed" in {
    val data: Option[dp.Data] = Some(
      dp.Data(
        List(activeProperty, inactiveProperty, differentPropertyTypeActiveProperty, differentPropertyTypeInactiveProperty)
      )
    )
    val response = GraphQlResponse(data, Nil)
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val properties: List[DisplayProperty] = displayPropertiesService.getDisplayProperties(consignmentId, token, None).futureValue
    properties.size should equal(2)
    properties.head.propertyName should equal("differentPropertyTypeProperty")
    properties.last.propertyName should equal("activeProperty")
  }

  "getDisplayProperties" should "not return any properties if none are active or are different property type to the given 'metadata type'" in {
    val data: Option[dp.Data] = Some(
      dp.Data(
        List(inactiveProperty, differentPropertyTypeInactiveProperty, differentPropertyTypeActiveProperty)
      )
    )
    val response = GraphQlResponse(data, Nil)
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val properties: List[DisplayProperty] = displayPropertiesService.getDisplayProperties(consignmentId, token, "propertyType".some).futureValue
    properties.size should equal(0)
  }

  "getDisplayProperties" should "return an error if property is missing a 'data type'" in {
    val data: Option[dp.Data] = Some(
      dp.Data(
        List(
          dp.DisplayProperties(
            "property1",
            List(dp.DisplayProperties.Attributes("ComponentType", Some("large text"), Text))
          )
        )
      )
    )
    val response = GraphQlResponse(data, Nil)
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val thrownException = intercept[Exception] {
      displayPropertiesService.getDisplayProperties(consignmentId, token, "propertyType".some).futureValue
    }

    thrownException.getMessage should equal("The future returned an exception of type: java.lang.Exception, with message: No datatype.")
  }

  "getDisplayProperties" should "return an error if the property's data type is invalid" in {
    val data: Option[dp.Data] = Some(
      dp.Data(
        List(
          dp.DisplayProperties(
            "property1",
            requiredAttributes(Some("InvalidDataType"))
          )
        )
      )
    )
    val response = GraphQlResponse(data, Nil)
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val thrownException = intercept[Exception] {
      displayPropertiesService.getDisplayProperties(consignmentId, token, "propertyType".some).futureValue
    }

    thrownException.getMessage should equal("The future returned an exception of type: java.lang.Exception, with message: Invalid data type Some(InvalidDataType).")
  }

  "getDisplayProperties" should "return an error if the property's ordinal value is invalid" in {
    val data: Option[dp.Data] = Some(
      dp.Data(
        List(
          dp.DisplayProperties(
            "property1",
            requiredAttributes() ++
              List(dp.DisplayProperties.Attributes("Ordinal", Some("nonIntString"), Text))
          )
        )
      )
    )
    val response = GraphQlResponse(data, Nil)
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val thrownException = intercept[Exception] {
      displayPropertiesService.getDisplayProperties(consignmentId, token, "propertyType".some).futureValue
    }

    thrownException.getMessage should equal("The future returned an exception of type: java.lang.NumberFormatException, with message: For input string: \"nonIntString\".")
  }

  "getDisplayProperties" should "return an error if the API call fails" in {
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

    displayPropertiesService.getDisplayProperties(consignmentId, token, "propertyType".some).failed.futureValue shouldBe a[HttpError]
  }

  "getDisplayProperties" should "throw an AuthorisationException if the API returns an auth error" in {
    val response = GraphQlResponse[dp.Data](None, List(NotAuthorisedError("some auth error", Nil, Nil)))
    when(displayPropertiesClient.getResult(token, dp.document, Some(dp.Variables(consignmentId))))
      .thenReturn(Future.successful(response))

    val results = displayPropertiesService.getDisplayProperties(consignmentId, token, "propertyType".some).failed.futureValue.asInstanceOf[AuthorisationException]

    results shouldBe a[AuthorisationException]
  }

  "toDisplayProperty" should "return valid display property based on attribute values" in {
    val property: DisplayProperty = displayPropertiesService.toDisplayProperty(activeProperty)
    property.active should equal(true)
    property.componentType should equal("componentType")
    property.dataType should equal(Text)
    property.displayName should equal("display name")
    property.description should equal("description value")
    property.editable should equal(true)
    property.group should equal("group")
    property.guidance should equal("guidance")
    property.label should equal("label")
    property.multiValue should equal(false)
    property.propertyName should equal("activeProperty")
    property.ordinal should equal(11)
    property.propertyType should equal("propertyType")
    property.unitType should equal("unitType")
    property.details should equal(Some(Details("summary", "text")))
    property.required should equal(true)
  }

  "toDisplayProperty" should "return default values for all optional property fields" in {
    val displayProperties = dp.DisplayProperties("property1", requiredAttributes())

    val property: DisplayProperty = displayPropertiesService.toDisplayProperty(displayProperties)
    property.active should equal(false)
    property.componentType should equal("")
    property.dataType should equal(Text)
    property.displayName should equal("")
    property.description should equal("")
    property.editable should equal(false)
    property.group should equal("")
    property.guidance should equal("")
    property.label should equal("")
    property.multiValue should equal(false)
    property.propertyName should equal("property1")
    property.ordinal should equal(0)
    property.propertyType should equal("")
    property.unitType should equal("")
    property.required should equal(false)
  }

  val detailsTable: TableFor2[Option[String], Option[String]] = Table(
    ("detailsSummary", "detailsText"),
    (Some("summary"), None),
    (None, Some("text")),
    (None, None)
  )

  forAll(detailsTable)((detailsSummary, detailsText) => {
    "toDisplayProperty" should s"return an empty details object if the summary is $detailsSummary and the text is $detailsText" in {
      val textAttributes = dp.DisplayProperties.Attributes("DetailsText", detailsText, Text)
      val summaryAttributes = dp.DisplayProperties.Attributes("DetailsText", detailsSummary, Text)
      val input = dp.DisplayProperties("test", List(textAttributes, summaryAttributes) ++ requiredAttributes())
      val property: DisplayProperty = displayPropertiesService.toDisplayProperty(input)
      property.details should equal(None)
    }
  })

  private def requiredAttributes(dataType: Option[String] = Some("text")): List[dp.DisplayProperties.Attributes] = {
    List(dp.DisplayProperties.Attributes("Datatype", dataType, Text))
  }
}
