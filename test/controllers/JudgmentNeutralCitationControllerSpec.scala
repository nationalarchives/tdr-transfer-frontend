package controllers

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import graphql.codegen.GetConsignmentMetadata.getConsignmentMetadata.GetConsignment.ConsignmentMetadata
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import play.api.Play.materializer
import play.api.i18n.DefaultMessagesApi
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, contentAsString, contentType, redirectLocation, status => playStatus, _}
import services.{ConsignmentMetadataService, ConsignmentService}
import testUtils.FrontEndTestHelper
import uk.gov.nationalarchives.tdr.schema.generated.BaseSchema._

import java.util.{Properties, UUID}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.Using

class JudgmentNeutralCitationControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global
  val consignmentMetadataService: ConsignmentMetadataService = mock[ConsignmentMetadataService]
  val consignmentId: UUID = UUID.randomUUID()

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  private def instantiateController() = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    import scala.jdk.CollectionConverters._
    val properties = new Properties()
    Using(Source.fromFile("conf/messages")) { source =>
      properties.load(source.bufferedReader())
    }
    val map = properties.asScala.toMap
    val testMessages = Map(
      "default" -> map
    )
    val messagesApi = new DefaultMessagesApi(testMessages)
    new JudgmentNeutralCitationController(
      getAuthorisedSecurityComponents,
      graphQLConfiguration,
      getValidJudgmentUserKeycloakConfiguration,
      consignmentService,
      consignmentMetadataService,
      messagesApi
    )
  }

  "JudgmentNeutralCitationController GET" should {
    "return OK and show neutral citation form" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setGetConsignmentMetadataResponse(wiremockServer, Nil.some)

      val result = controller
        .addNCN(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/neutral-citation").withCSRFToken)

      playStatus(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      checkContent(body)
    }

    "return OK and show neutral citation form with saved ncn showing" in {
      val ncn = "[2024] EWCOP 123 (T1)"
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val metadata = ConsignmentMetadata("JudgmentNeutralCitation", ncn) :: Nil
      setGetConsignmentMetadataResponse(wiremockServer, metadata.some)

      val result = controller
        .addNCN(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/neutral-citation").withCSRFToken)

      playStatus(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      checkContent(body, ncn)
    }

    "return OK and show neutral citation form with saved no-ncn and judgment reference showing" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val metadata = ConsignmentMetadata("JudgmentNoNeutralCitation", "true") :: ConsignmentMetadata("JudgmentReference", "details") :: Nil
      setGetConsignmentMetadataResponse(wiremockServer, metadata.some)

      val result = controller
        .addNCN(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/neutral-citation").withCSRFToken)

      playStatus(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      checkContent(body, noNCN = true, reference = "details")
    }
  }

  "JudgmentNeutralCitationController POST" should {
    "return BadRequest and show error when no NCN and checkbox not selected" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val result = controller
        .validateNCN(consignmentId)
        .apply(FakeRequest(POST, s"/judgment/$consignmentId/neutral-citation").withCSRFToken)

      playStatus(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      checkContent(body, inputError = "govuk-input--error")
      body must include("""<a href="#error-judgment_neutral_citation">Enter a valid NCN, or select &#x27;Original judgment to this update does not have an NCN&#x27;</a>""")
    }

    "return BadRequest and show error NCN less than 10 characters" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)

      val result = controller
        .validateNCN(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/neutral-citation?")
            .withFormUrlEncodedBody("judgment_neutral_citation" -> "123")
            .withCSRFToken
        )

      playStatus(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      checkContent(body, ncn = "123", inputError = "govuk-input--error")
      body must include("""<a href="#error-judgment_neutral_citation">Neutral citation number must be between 10 and 100 characters</a>""")
    }

    "return BadRequest and show error NCN more than 100 characters" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      val longNcn = "a" * 101

      val result = controller
        .validateNCN(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/neutral-citation?")
            .withFormUrlEncodedBody("judgment_neutral_citation" -> longNcn)
            .withCSRFToken
        )

      playStatus(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      checkContent(body, ncn = longNcn, inputError = "govuk-input--error")
      body must include("Neutral citation number must be between 10 and 100 characters")
    }

    "return BadRequest and show error reference more than 500 characters" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      val longRef = "a" * 501

      val result = controller
        .validateNCN(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/neutral-citation?")
            .withFormUrlEncodedBody(judgment_reference -> longRef, judgment_no_neutral_citation -> "true")
            .withCSRFToken
        )

      playStatus(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      checkContent(body, noNCN = true, reference = longRef)
      body must include("""<a href="#error-judgment_reference">&#x27;Provide any details...&#x27; must be 500 characters or less</a>""")
    }

    "redirect to upload page when NCN is provided" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val validNcn = "[2025] EWCOP 123 (T1)"
      val metadata = Map(judgment_neutral_citation -> validNcn, judgment_no_neutral_citation -> "false", judgment_reference -> "")
      val metadataRowCaptor: ArgumentCaptor[Map[String, String]] = ArgumentCaptor.forClass(classOf[Map[String, String]])
      when(consignmentMetadataService.addOrUpdateConsignmentMetadata(any[UUID], metadataRowCaptor.capture(), any[BearerAccessToken])).thenReturn(Future.successful(Nil))

      val result = controller
        .validateNCN(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/neutral-citation")
            .withFormUrlEncodedBody("judgment_neutral_citation" -> validNcn)
            .withCSRFToken
        )
      playStatus(result) mustBe SEE_OTHER
      val redirect = redirectLocation(result).value
      redirect must startWith(s"/judgment/$consignmentId/upload")
      metadataRowCaptor.getValue mustBe metadata
    }

    "redirect to upload page when 'no-ncn' checkbox selected" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")

      val result = controller
        .validateNCN(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/neutral-citation")
            .withFormUrlEncodedBody("judgment_no_neutral_citation" -> "true", "judgment_reference" -> "An example reference")
            .withCSRFToken
        )

      playStatus(result) mustBe SEE_OTHER
      val redirect = redirectLocation(result).value
      redirect mustBe s"/judgment/$consignmentId/upload"
    }
  }

  def checkContent(body: String, ncn: String = "", noNCN: Boolean = false, reference: String = "", inputError: String = ""): Unit = {
    body must include(s"""<a href="/judgment/$consignmentId/tell-us-more" class="govuk-back-link">Back</a>""")
    body must include("""<h1 class="govuk-heading-l">Provide the neutral citation number (NCN) for the original judgment</h1>""")
    body must include(
      s"""<input class="govuk-input $inputError" id="neutral-citation" name="judgment_neutral_citation" type="text" value="$ncn" ${if (noNCN) "aria-disabled=\"true\"" else ""}>"""
    )
    body must include(
      s"""<input class="govuk-checkboxes__input" id="no-ncn" name="judgment_no_neutral_citation" type="checkbox" value="true" data-aria-controls="conditional-no-ncn" ${if (noNCN)
          "checked"
        else ""}>""".stripMargin
    )
    body must include(s"""<div class="govuk-checkboxes__conditional ${if (!noNCN) "govuk-checkboxes__conditional--hidden" else ""}" id="conditional-no-ncn">""")
    if (reference.length > 500)
      body must include(
        s"""<input class="govuk-input govuk-input--error" id="judgment_reference" name="judgment_reference" type="text" value="$reference" aria-describedby=&quot;judgment-reference-error&quot;>"""
      )
    else
      body must include(s"""<input class="govuk-input " id="judgment_reference" name="judgment_reference" type="text" value="$reference" >""")
  }
}
