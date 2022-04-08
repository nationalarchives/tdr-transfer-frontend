package controllers

import java.util.UUID
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import configuration.GraphQLConfiguration
import io.circe.Printer
import org.pac4j.play.scala.SecurityComponents
import play.api.test.CSRFTokenHelper.CSRFRequest
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, contentAsString, defaultAwaitTimeout, status}
import services.ConsignmentService
import util.FrontEndTestHelper
import graphql.codegen.GetConsignmentReference.{getConsignmentReference => gcr}
import io.circe.syntax.EncoderOps
import io.circe.generic.auto._
import play.api.http.Status.FORBIDDEN
import play.api.mvc.Result

import scala.concurrent.{ExecutionContext, Future}

class TransferCompleteControllerSpec extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val wiremockServer = new WireMockServer(9006)
  val wiremockExportServer = new WireMockServer(9007)

  override def beforeEach(): Unit = {
    wiremockServer.start()
    wiremockExportServer.start()
  }

  override def afterEach(): Unit = {
    wiremockServer.resetAll()
    wiremockExportServer.resetAll()
    wiremockServer.stop()
    wiremockExportServer.stop()
  }

  "TransferCompleteController GET" should {
    "render the success page if the export was triggered successfully" in {
      setConsignmentReferenceResponse()
      val transferCompleteSubmit = callTransferComplete("consignment")
      contentAsString(transferCompleteSubmit) must include("Transfer complete")
      contentAsString(transferCompleteSubmit) must include("TEST-TDR-2021-GB")
      contentAsString(transferCompleteSubmit) must include("Your records have now been transferred to The National Archives.")
      contentAsString(transferCompleteSubmit) must include(
      """    <a href="https://www.smartsurvey.co.uk/s/tdr-feedback/" class="govuk-link" rel="noreferrer noopener" target="_blank">
        What did you think of this service? (opens in new tab)""")
      contentAsString(transferCompleteSubmit) must include (s"""" href="/faq">""")
    }

    "render the success page if the export was triggered successfully for a judgment user" in {
      setConsignmentReferenceResponse()
      val transferCompleteSubmit = callTransferComplete("judgment")
      contentAsString(transferCompleteSubmit) must include("Transfer complete")
      contentAsString(transferCompleteSubmit) must include("TEST-TDR-2021-GB")
      contentAsString(transferCompleteSubmit) must include("Your file has now been transferred to The National Archives.")
      contentAsString(transferCompleteSubmit) must include(
        """    <a href="https://www.smartsurvey.co.uk/s/5YDPSA/" class="govuk-link" rel="noreferrer noopener" target="_blank">
        What did you think of this service? (opens in new tab)""")
      contentAsString(transferCompleteSubmit) must include (s"""" href="/judgment/faq">""")
    }
  }

  forAll(userChecks) { (_, url) =>
    s"The $url upload page" should {
      s"return 403 if the url doesn't match the consignment type" in {
        setConsignmentReferenceResponse()
        val controller = instantiateTransferCompleteController(getAuthorisedSecurityComponents, url)
        val consignmentId = UUID.randomUUID()
        setConsignmentTypeResponse(wiremockServer, url)

        val transferCompleteSubmit = if (url.equals("judgment")) {
          setConsignmentTypeResponse(wiremockServer, "judgment")
          controller.transferComplete(consignmentId)
            .apply(FakeRequest(GET, s"/consignment/$consignmentId/transfer-complete").withCSRFToken)
        } else {
          setConsignmentTypeResponse(wiremockServer, "standard")
          controller.judgmentTransferComplete(consignmentId)
            .apply(FakeRequest(GET, s"/judgment/$consignmentId/transfer-complete").withCSRFToken)
        }
        status(transferCompleteSubmit) mustBe FORBIDDEN
      }
    }
  }

  private def setConsignmentReferenceResponse(): StubMapping = {
    val client = new GraphQLConfiguration(app.configuration).getClient[gcr.Data, gcr.Variables]()
    val consignmentReferenceResponse: gcr.GetConsignment = new gcr.GetConsignment("TEST-TDR-2021-GB")
    val data: client.GraphqlData = client.GraphqlData(Some(gcr.Data(Some(consignmentReferenceResponse))), List())
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getConsignmentReference"))
      .willReturn(okJson(dataString)))
  }

  private def instantiateTransferCompleteController(securityComponents: SecurityComponents, path: String) = {
    val graphQLConfiguration = new GraphQLConfiguration(app.configuration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    if (path.equals("judgment")) {
      new TransferCompleteController(securityComponents, getValidJudgmentUserKeycloakConfiguration, consignmentService)
    } else {
      new TransferCompleteController(securityComponents, getValidKeycloakConfiguration, consignmentService)
    }
  }

  private def callTransferComplete(path: String): Future[Result] = {
    val controller = instantiateTransferCompleteController(getAuthorisedSecurityComponents, path)
    val consignmentId = UUID.randomUUID()
    if (path.equals("judgment")) {
      setConsignmentTypeResponse(wiremockServer, "judgment")
      controller.judgmentTransferComplete(consignmentId)
        .apply(FakeRequest(GET, s"/$path/$consignmentId/transfer-complete").withCSRFToken)
    } else {
      setConsignmentTypeResponse(wiremockServer, "standard")
      controller.transferComplete(consignmentId)
        .apply(FakeRequest(GET, s"/$path/$consignmentId/transfer-complete").withCSRFToken)
    }
  }
}
