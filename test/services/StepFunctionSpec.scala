package services

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, anyUrl, containing, exactly, post, postRequestedFor}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.{ConfigLoader, Configuration}
import uk.gov.nationalarchives.aws.utils.stepfunction.StepFunctionUtils

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class StepFunctionSpec extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures {
  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  implicit val encoder: Encoder[StepFunctionInput] = deriveEncoder[StepFunctionInput]

  override def beforeAll(): Unit = {
    wiremockSfnServer.start()
  }

  override def afterAll(): Unit = {
    wiremockSfnServer.stop()
  }

  override def beforeEach(): Unit = {
    wiremockSfnServer.start()
  }

  override def afterEach(): Unit = {
    wiremockSfnServer.stop()
  }

  def mockSfnResponseOk(): StubMapping = {
    wiremockSfnServer.stubFor(
      post(anyUrl())
        .willReturn(aResponse().withStatus(200))
    )
  }

  def mockSfnResponseNotOk(): StubMapping = {
    wiremockSfnServer.stubFor(
      post(anyUrl())
        .willReturn(aResponse().withStatus(500))
    )
  }

  case class StepFunctionInput(aParameter: String)

  val wiremockSfnServer = new WireMockServer(9003)

  "triggerStepFunction" should "trigger step function with the correct request" in {
    val config = mock[Configuration]
    val execId = UUID.randomUUID()
    mockSfnResponseOk()
    when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("http://localhost:9003")

    val service = new StepFunction(config)
    service.triggerStepFunction("stateMachineArn", StepFunctionInput("a value"), "name", execId).futureValue

    wiremockSfnServer.verify(
      exactly(1),
      postRequestedFor(anyUrl())
        .withRequestBody(containing(s""""stateMachineArn":"stateMachineArn","name":"$execId","input":"{\\"aParameter\\":\\"a value\\"}""""))
    )
  }

  "triggerStepFunction" should "return an error when step function request fails" in {
    val config = mock[Configuration]
    val execId = UUID.randomUUID()
    mockSfnResponseNotOk()
    when(config.get[String](any[String])(any[ConfigLoader[String]])).thenReturn("http://localhost:9003")

    val service = new StepFunction(config)

    val error = intercept[RuntimeException] {
      service.triggerStepFunction("stateMachineArn", StepFunctionInput("a value"), "name", execId).futureValue
    }

    error.getMessage shouldBe
      "The future returned an exception of type: java.lang.RuntimeException, with message: Step function name trigger failed: " +
      "software.amazon.awssdk.services.sfn.model.SfnException: Service returned HTTP status code 500 (Service: Sfn, Status Code: 500," +
      " Request ID: null) (SDK Attempt Count: 4)."
  }
}
