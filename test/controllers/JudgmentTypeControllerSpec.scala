package controllers

import cats.implicits.catsSyntaxOptionId
import com.github.tomakehurst.wiremock.WireMockServer
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.{ApplicationConfig, GraphQLConfiguration}
import graphql.codegen.GetConsignmentMetadata.getConsignmentMetadata.GetConsignment.ConsignmentMetadata
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.mockito.{ArgumentCaptor, ArgumentMatcher}
import play.api.Play.materializer
import play.api.test.CSRFTokenHelper._
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, contentType, status, status => playStatus, _}
import services.{ConsignmentMetadataService, ConsignmentService}
import testUtils.FrontEndTestHelper
import uk.gov.nationalarchives.tdr.schema.generated.BaseSchema._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class JudgmentTypeControllerSpec extends FrontEndTestHelper {

  implicit val ec: ExecutionContext = ExecutionContext.global
  val consignmentMetadataService: ConsignmentMetadataService = mock[ConsignmentMetadataService]
  val defaultNcnMetadata: Map[String, String] = Map(judgment_neutral_citation -> "", judgment_no_neutral_citation -> "", judgment_reference -> "")
  val consignmentId: UUID = UUID.randomUUID()

  val wiremockServer = new WireMockServer(9006)

  override def beforeEach(): Unit = {
    wiremockServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockServer.stop()
  }

  "JudgmentTypeController GET" should {
    "return OK and show judgment document type page and the first option should be selected by default" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setGetConsignmentMetadataResponse(wiremockServer)

      val result = controller
        .selectJudgmentType(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/tell-us-more").withCSRFToken)

      playStatus(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      checkContent(body, "judgment")
    }

    "return OK and show judgment document type page and the update option should be selected when the an update option already saved in the DB" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val metadata = ConsignmentMetadata("JudgmentType", "judgment") :: ConsignmentMetadata("JudgmentUpdateType", "Typo") :: Nil
      setGetConsignmentMetadataResponse(wiremockServer, metadata.some)

      val result = controller
        .selectJudgmentType(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/tell-us-more").withCSRFToken)

      playStatus(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      checkContent(body, "update", List("Typo"))
    }

    "return OK and show judgment document type page and the press_summary option should be selected when the an press_summary option already saved in the DB" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      val metadata = ConsignmentMetadata("JudgmentType", "press_summary") :: Nil
      setGetConsignmentMetadataResponse(wiremockServer, metadata.some)

      val result = controller
        .selectJudgmentType(consignmentId)
        .apply(FakeRequest(GET, s"/judgment/$consignmentId/tell-us-more").withCSRFToken)

      playStatus(result) mustBe OK
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      checkContent(body, "press_summary")
    }
  }

  "JudgmentTypeController POST" should {
    "return judgment document type page error when update is selected but no reason is provided" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      setGetConsignmentMetadataResponse(wiremockServer)

      val result = controller
        .submitJudgmentType(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/tell-us-more")
            .withFormUrlEncodedBody("judgment_type" -> "update")
            .withCSRFToken
        )

      playStatus(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      checkContent(body, "update", Nil)
    }

    "return judgment document type page error when update is selected but update details are more than 1000 characters" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      setGetConsignmentMetadataResponse(wiremockServer)

      val result = controller
        .submitJudgmentType(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/tell-us-more")
            .withFormUrlEncodedBody("judgment_type" -> "update", "judgment_update_details" -> "update" * 200)
            .withCSRFToken
        )

      playStatus(result) mustBe BAD_REQUEST
      contentType(result) mustBe Some("text/html")
      val body = contentAsString(result)
      checkContent(body, "update", Nil, updateDetailsError = true)
    }

    "redirect to before upload page when the user selects the first option and submits the form" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      setGetConsignmentMetadataResponse(wiremockServer)

      val metadata = defaultNcnMetadata ++ Map(judgment_type -> "judgment", judgment_update -> "false", judgment_update_type -> "", judgment_update_details -> "")
      val metadataRowCaptor: ArgumentCaptor[Map[String, String]] = ArgumentCaptor.forClass(classOf[Map[String, String]])
      when(consignmentMetadataService.addOrUpdateConsignmentMetadata(any[UUID], metadataRowCaptor.capture(), any[BearerAccessToken])).thenReturn(Future.successful(Nil))

      val result = controller
        .submitJudgmentType(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/tell-us-more")
            .withFormUrlEncodedBody("judgment_type" -> "judgment")
            .withCSRFToken
        )

      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(s"/judgment/$consignmentId/before-uploading"))
      metadataRowCaptor.getValue mustEqual metadata
    }

    "redirect to NCN page when the user selects the second option option and submits the form" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      setGetConsignmentMetadataResponse(wiremockServer)

      val metadata = defaultNcnMetadata ++ Map(judgment_type -> "judgment", judgment_update -> "true", judgment_update_type -> "Typo", judgment_update_details -> "")
      val metadataRowCaptor: ArgumentCaptor[Map[String, String]] = ArgumentCaptor.forClass(classOf[Map[String, String]])
      when(consignmentMetadataService.addOrUpdateConsignmentMetadata(any[UUID], metadataRowCaptor.capture(), any[BearerAccessToken])).thenReturn(Future.successful(Nil))

      val result = controller
        .submitJudgmentType(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/tell-us-more")
            .withFormUrlEncodedBody("judgment_type" -> "update", "judgment_update_type" -> "Typo")
            .withCSRFToken
        )

      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(s"/judgment/$consignmentId/neutral-citation"))
      metadataRowCaptor.getValue mustEqual metadata
    }

    "redirect to NCN page when the user selects the third option option and submits the form" in {
      val controller = instantiateController()
      setConsignmentTypeResponse(wiremockServer, "judgment")
      setConsignmentReferenceResponse(wiremockServer)
      setGetConsignmentMetadataResponse(wiremockServer)

      val metadata = defaultNcnMetadata ++ Map(judgment_type -> "press_summary", judgment_update -> "false", judgment_update_type -> "", judgment_update_details -> "")
      val metadataRowCaptor: ArgumentCaptor[Map[String, String]] = ArgumentCaptor.forClass(classOf[Map[String, String]])
      when(consignmentMetadataService.addOrUpdateConsignmentMetadata(any[UUID], metadataRowCaptor.capture(), any[BearerAccessToken])).thenReturn(Future.successful(Nil))

      val result = controller
        .submitJudgmentType(consignmentId)
        .apply(
          FakeRequest(POST, s"/judgment/$consignmentId/tell-us-more")
            .withFormUrlEncodedBody("judgment_type" -> "press_summary")
            .withCSRFToken
        )

      status(result) mustBe SEE_OTHER
      redirectLocation(result) must be(Some(s"/judgment/$consignmentId/neutral-citation"))
      metadataRowCaptor.getValue mustEqual metadata
    }
  }

  private def checkContent(body: String, judgmentType: String, updateReason: List[String] = Nil, updateDetailsError: Boolean = false): Unit = {
    body must include(s"""<a href="/judgment/homepage" class="govuk-back-link">Back</a>""")
    body must include(s"""<h1 class="govuk-fieldset__heading">Tell us about your document</h1>""")
    body must include(
      s"""<input class="govuk-radios__input" id="judgmentType" name="judgment_type" type="radio" value="judgment" ${if (judgmentType == "judgment") "checked" else ""}>"""
    )
    body must include(
      s"""<input class="govuk-radios__input" id="judgmentType-2" name="judgment_type" type="radio" value="update" data-aria-controls="conditional-judgmentType-2" ${if (
          judgmentType == "update"
        ) "checked"
        else ""}>"""
    )
    body must include(s"""<input class="govuk-radios__input" id="judgmentType-3" name="judgment_type" type="radio" value="press_summary" ${if (judgmentType == "press_summary")
        "checked"
      else ""}>""")
    body must include(s"""    <div class="govuk-form-group ${if (judgmentType == "update" && updateReason.isEmpty) "govuk-form-group--error" else ""}">
         |        <fieldset class="govuk-fieldset">
         |            <div class="govuk-hint">Select the reasons for the update:</div>""".stripMargin)
    body must include(
      s"""    <div class="govuk-checkboxes__item">
         |        <input class="govuk-checkboxes__input" id="typo" name="judgment_update_type" type="checkbox" value="Typo" ${if (updateReason.contains("Typo")) "checked"
        else ""} >
         |        <label class="govuk-label govuk-checkboxes__label" for="typo">
         |        Typo
         |        </label>
         |    </div>""".stripMargin
    )
    if (judgmentType == "update" && updateReason.isEmpty) {
      body must include("""<a href="#error-judgment_update_type">Select at least one reason for the update</a>""")
      body must include(
        """<p class="govuk-error-message" id="error-judgment_update_type">
          |        <span class="govuk-visually-hidden">Error:</span>
          |        Select at least one reason for the update
          |    </p>""".stripMargin
      )
    }
    if (updateDetailsError) {
      body must include("""<a href="#error-judgment_update_details">&#x27;Add more details about this update&#x27; must be less than 1000 characters</a>""")
    }
  }

  private def instantiateController() = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val applicationConfig = new ApplicationConfig(app.configuration)

    new JudgmentTypeController(
      getAuthorisedSecurityComponents,
      graphQLConfiguration,
      getValidJudgmentUserKeycloakConfiguration,
      consignmentService,
      consignmentMetadataService,
      applicationConfig
    )
  }
}
