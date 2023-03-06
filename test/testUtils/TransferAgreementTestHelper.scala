package testUtils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import controllers.{TransferAgreementPart2Controller, TransferAgreementPart1Controller}
import graphql.codegen.AddTransferAgreementCompliance.{addTransferAgreementCompliance => atac}
import graphql.codegen.AddTransferAgreementPrivateBeta.{addTransferAgreementPrivateBeta => atapb}
import graphql.codegen.GetConsignment.{getConsignment => gc}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures._
import play.api.Configuration
import services.{ConsignmentService, ConsignmentStatusService, TransferAgreementService}
import testUtils.DefaultMockFormOptions.{expectedPart2Options, expectedPart1Options}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions

import scala.concurrent.ExecutionContext

class TransferAgreementTestHelper(wireMockServer: WireMockServer) extends FrontEndTestHelper {
  implicit val ec: ExecutionContext = ExecutionContext.global

  val privateBeta = "privateBeta"
  val part2 = "part2"
  val userType = "standard"

  def mockGetConsignmentGraphqlResponse(config: Configuration, consignmentType: String = "standard"): StubMapping = {

    val client = new GraphQLConfiguration(config).getClient[gc.Data, gc.Variables]()
    val data: client.GraphqlData = client.GraphqlData(Some(gc.Data(None)), List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    if (dataString.nonEmpty) {
      wireMockServer.stubFor(
        post(urlEqualTo("/graphql"))
          .withRequestBody(containing("getConsignment"))
          .willReturn(okJson(dataString))
      )
    }

    setConsignmentTypeResponse(wireMockServer, consignmentType)
  }

  def getTransferAgreementForm(optionsType: String): Seq[(String, String)] = {
    val value = "true"

    val options = Map(
      "privateBeta" ->
        Seq(
          ("publicRecord", value),
          ("crownCopyright", value),
          ("english", value)
        ),
      "part2" ->
        Seq(
          ("droAppraisalSelection", value),
          ("droSensitivity", value),
          ("openRecords", value)
        )
    )

    options(optionsType)
  }

  def instantiateTransferAgreementPart1Controller(
      securityComponents: SecurityComponents,
      config: Configuration,
      keycloakConfiguration: KeycloakConfiguration = getValidKeycloakConfiguration
  ): TransferAgreementPart1Controller = {

    val graphQLConfiguration = new GraphQLConfiguration(config)
    val transferAgreementService = new TransferAgreementService(graphQLConfiguration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    val applicationConfig = new ApplicationConfig(config)

    new TransferAgreementPart1Controller(
      securityComponents,
      new GraphQLConfiguration(config),
      transferAgreementService,
      keycloakConfiguration,
      consignmentService,
      consignmentStatusService,
      applicationConfig
    )
  }

  def instantiateTransferAgreementPart2Controller(
      securityComponents: SecurityComponents,
      config: Configuration,
      keycloakConfiguration: KeycloakConfiguration = getValidKeycloakConfiguration
  ): TransferAgreementPart2Controller = {
    val graphQLConfiguration = new GraphQLConfiguration(config)
    val transferAgreementService = new TransferAgreementService(graphQLConfiguration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)
    val applicationConfig = new ApplicationConfig(config)

    new TransferAgreementPart2Controller(
      securityComponents,
      new GraphQLConfiguration(config),
      transferAgreementService,
      keycloakConfiguration,
      consignmentService,
      consignmentStatusService,
      applicationConfig
    )
  }

  def stubTAPrivateBetaResponse(transferAgreement: Option[atapb.AddTransferAgreementPrivateBeta] = None, config: Configuration, errors: List[GraphQLClient.Error] = Nil): Unit = {
    val client = new GraphQLConfiguration(config).getClient[atapb.Data, atapb.Variables]()

    val data: client.GraphqlData =
      client.GraphqlData(
        transferAgreement.map(ta => atapb.Data(ta)), // Please ignore the "Type mismatch" error that IntelliJ displays, as it is incorrect.
        errors
      )
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    wireMockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString))
    )
  }

  def stubTAPart2Response(transferAgreement: Option[atac.AddTransferAgreementCompliance] = None, config: Configuration, errors: List[GraphQLClient.Error] = Nil): Unit = {
    val client = new GraphQLConfiguration(config).getClient[atac.Data, atac.Variables]()

    val data: client.GraphqlData =
      client.GraphqlData(
        transferAgreement.map(ta => atac.Data(ta)), // Please ignore the "Type mismatch" error that IntelliJ displays, as it is incorrect.
        errors
      )
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    wireMockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .willReturn(okJson(dataString))
    )
  }

  def checkForExpectedTAPageContent(pageAsString: String, taAlreadyConfirmed: Boolean = true, confirmationMessage: String): Unit = {
    if (taAlreadyConfirmed) {
      pageAsString must include("""            <h2 class="success-summary__title">You have already confirmed all statements</h2>""")
      pageAsString must include("""            <p class="govuk-body">Click 'Continue' to proceed with your transfer.</p>""")
    } else {
      pageAsString must include(
        s"""        <p class="govuk-body">$confirmationMessage</p>"""
      )
    }
  }
}
