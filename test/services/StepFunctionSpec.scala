package services

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, anyUrl, containing, exactly, post, postRequestedFor}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import configuration.ApplicationConfig
import io.circe.Encoder
import io.circe.generic.semiauto.deriveEncoder
import org.mockito.Mockito.when
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.mockito.MockitoSugar.mock

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class StepFunctionSpec extends AnyFlatSpec with BeforeAndAfterEach with BeforeAndAfterAll with ScalaFutures {
  override implicit def patienceConfig: PatienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  implicit val encoder: Encoder[StepFunctionInput] = deriveEncoder[StepFunctionInput]
  val applicationConfig: ApplicationConfig = mock[ApplicationConfig]

  override def beforeAll(): Unit = {
    wiremockSfnServer.start()
  }

  override def afterAll(): Unit = {
    wiremockSfnServer.stop()
  }

  override def afterEach(): Unit = {
    wiremockSfnServer.resetAll()
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
    val execId = "TestExecId"
    mockSfnResponseOk()
    when(applicationConfig.stepFunctionEndpoint).thenReturn("http://localhost:9003")

    val service = new StepFunction(applicationConfig)
    service.triggerStepFunction("stateMachineArn", StepFunctionInput("a value"), "name", execId).futureValue

    wiremockSfnServer.verify(
      exactly(1),
      postRequestedFor(anyUrl())
        .withRequestBody(containing(s""""stateMachineArn":"stateMachineArn","name":"$execId","input":"{\\"aParameter\\":\\"a value\\"}""""))
    )
  }

  "triggerStepFunction" should "return an error when step function request fails" in {
    val execId = "TestExecId"
    mockSfnResponseNotOk()
    when(applicationConfig.stepFunctionEndpoint).thenReturn("http://localhost:9003")

    val service = new StepFunction(applicationConfig)

    val error = intercept[Exception] {
      service.triggerStepFunction("stateMachineArn", StepFunctionInput("a value"), "name", execId).futureValue
    }

    error.getMessage shouldBe
      "The future returned an exception of type: java.lang.Exception, with message: Step function name trigger failed: " +
      "software.amazon.awssdk.services.sfn.model.SfnException: Service returned HTTP status code 500 (Service: Sfn, Status Code: 500," +
      " Request ID: null) (SDK Attempt Count: 4)."
  }
}
