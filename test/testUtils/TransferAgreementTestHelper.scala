package testUtils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.{TransferAgreementComplianceController, TransferAgreementPrivateBetaController}
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
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions

import scala.concurrent.ExecutionContext

class TransferAgreementTestHelper(wireMockServer: WireMockServer) extends FrontEndTestHelper{
  implicit val ec: ExecutionContext = ExecutionContext.global

  lazy val privateBetaOptions = Map(
    "publicRecord" -> (
      "I confirm that the records are Public Records.",
      "All records must be confirmed as public before proceeding"
    ),
    "crownCopyright" -> (
      "I confirm that the records are all Crown Copyright.",
      "All records must be confirmed Crown Copyright before proceeding"
    ),
    "english" -> (
      "I confirm that the records are all in English.",
      "All records must be confirmed as English language before proceeding"
    )
  )

  lazy val complianceOptions = Map(
    "droAppraisalSelection" -> (
      "I confirm that the Departmental Records Officer (DRO) has signed off on the appraisal and selection",
      "Departmental Records Officer (DRO) must have signed off the appraisal and selection decision for records"
    ),
    "droSensitivity" -> (
      "I confirm that the Departmental Records Officer (DRO) has signed off on the sensitivity review.",
      "Departmental Records Officer (DRO) must have signed off sensitivity review"
    ),
    "openRecords" -> (
      "I confirm that all records are open and no Freedom of Information (FOI) exemptions apply to these records.",
      "All records must be open"
    )
  )

  lazy val checkHtmlOfPrivateBetaFormOptions = new FormTester(privateBetaOptions, "")
  lazy val checkHtmlOfComplianceFormOptions = new FormTester(complianceOptions, "")

  val privateBeta = "privateBeta"
  val compliance = "compliance"
  val userType = "standard"

  def mockGetConsignmentGraphqlResponse(config: Configuration,
                                        consignmentType: String = "standard"): StubMapping = {

    val client = new GraphQLConfiguration(config).getClient[gc.Data, gc.Variables]()
    val data: client.GraphqlData = client.GraphqlData(
      Some(gc.Data(None)),
      List(GraphQLClient.Error("Error", Nil, Nil, Some(Extensions(Some("NOT_AUTHORISED"))))))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    if(dataString.nonEmpty) {
      wireMockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignment"))
        .willReturn(okJson(dataString)))
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
      "compliance" ->
        Seq(
          ("droAppraisalSelection", value),
          ("droSensitivity", value),
          ("openRecords", value)
        )
    )

    options(optionsType)
  }

  def instantiateTransferAgreementPrivateBetaController(securityComponents: SecurityComponents,
                                                        config: Configuration,
                                                        keycloakConfiguration: KeycloakConfiguration =
                                                        getValidKeycloakConfiguration): TransferAgreementPrivateBetaController = {

    val graphQLConfiguration = new GraphQLConfiguration(config)
    val transferAgreementService = new TransferAgreementService(graphQLConfiguration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

    new TransferAgreementPrivateBetaController(securityComponents, new GraphQLConfiguration(config),
      transferAgreementService, keycloakConfiguration, consignmentService, consignmentStatusService)
  }

  def instantiateTransferAgreementComplianceController(securityComponents: SecurityComponents,
                                                       config: Configuration,
                                                       keycloakConfiguration: KeycloakConfiguration =
                                                       getValidKeycloakConfiguration): TransferAgreementComplianceController = {
    val graphQLConfiguration = new GraphQLConfiguration(config)
    val transferAgreementService = new TransferAgreementService(graphQLConfiguration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)
    val consignmentStatusService = new ConsignmentStatusService(graphQLConfiguration)

    new TransferAgreementComplianceController(securityComponents, new GraphQLConfiguration(config),
      transferAgreementService, keycloakConfiguration, consignmentService, consignmentStatusService)
  }

  def stubTAPrivateBetaResponse(transferAgreement: Option[atapb.AddTransferAgreementPrivateBeta] = None,
                                  config: Configuration,
                                  errors: List[GraphQLClient.Error] = Nil): Unit = {
    val client = new GraphQLConfiguration(config).getClient[atapb.Data, atapb.Variables]()

    val data: client.GraphqlData =
      client.GraphqlData(
        transferAgreement.map(ta => atapb.Data(ta)),  // Please ignore the "Type mismatch" error that IntelliJ displays, as it is incorrect.
        errors
      )
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    wireMockServer.stubFor(post(urlEqualTo("/graphql"))
      .willReturn(okJson(dataString)))
  }

  def stubTAComplianceResponse(transferAgreement: Option[atac.AddTransferAgreementCompliance] = None,
                               config: Configuration,
                               errors: List[GraphQLClient.Error] = Nil): Unit = {
    val client = new GraphQLConfiguration(config).getClient[atac.Data, atac.Variables]()

    val data: client.GraphqlData =
      client.GraphqlData(
        transferAgreement.map(ta => atac.Data(ta)),  // Please ignore the "Type mismatch" error that IntelliJ displays, as it is incorrect.
        errors
      )
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    wireMockServer.stubFor(post(urlEqualTo("/graphql"))
      .willReturn(okJson(dataString)))
  }

  def checkForExpectedTAPageContent(pageAsString: String, taAlreadyConfirmed: Boolean=true): Unit = {
    if(taAlreadyConfirmed) {
      pageAsString must include("""            <h2 class="success-summary__title">You have already confirmed all statements</h2>""")
      pageAsString must include("""            <p class="govuk-body">Click 'Continue' to proceed with your transfer.</p>""")
    } else {
      pageAsString must include (
        """        <p class="govuk-body">You must confirm all statements before proceeding. """ +
        """If you cannot, please close your browser and contact your transfer advisor.</p>"""
      )
    }
  }
}
