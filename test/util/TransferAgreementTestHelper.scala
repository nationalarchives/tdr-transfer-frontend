package util

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import configuration.{GraphQLConfiguration, KeycloakConfiguration}
import controllers.{TransferAgreementPrivateBetaController, TransferAgreementComplianceController}
import graphql.codegen.AddTransferAgreementCompliance.{addTransferAgreementCompliance => atac}
import graphql.codegen.AddTransferAgreementPrivateBeta.{addTransferAgreementPrivateBeta => atapb}
import graphql.codegen.GetConsignment.{getConsignment => gc}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.CurrentStatus
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import org.scalatest.concurrent.ScalaFutures._
import play.api.Configuration
import services.{ConsignmentService, TransferAgreementService}
import uk.gov.nationalarchives.tdr.GraphQLClient
import uk.gov.nationalarchives.tdr.GraphQLClient.Extensions

import scala.concurrent.ExecutionContext

class TransferAgreementTestHelper(wireMockServer: WireMockServer) extends FrontEndTestHelper{
  implicit val ec: ExecutionContext = ExecutionContext.global

  val privateBetaOptions = Map(
    "publicRecord" -> "I confirm that the records are Public Records.",
    "crownCopyright" -> "I confirm that the records are all Crown Copyright.",
    "english" -> "I confirm that the records are all in English."
  )

  val complianceOptions = Map(
    "droAppraisalSelection" -> "I confirm that the Departmental Records Officer (DRO) has signed off on the appraisal and selection",
    "droSensitivity" -> "I confirm that the Departmental Records Officer (DRO) has signed off on the sensitivity review.",
    "openRecords" -> "I confirm that all records are open and no Freedom of Information (FOI) exemptions apply to these records."
  )

  val checkHtmlOfPrivateBetaFormOptions = new CheckHtmlOfFormOptions(privateBetaOptions, "")
  val checkHtmlOfComplianceFormOptions = new CheckHtmlOfFormOptions(complianceOptions, "")

  val privateBeta = "privateBeta"
  val compliance = "compliance"

  def mockGetConsignmentStatusGraphqlResponse(config: Configuration, taStatus: Option[String]=None, consignmentType: String = "standard"): StubMapping = {

    val client = new GraphQLConfiguration(config).getClient[gcs.Data, gcs.Variables]()
    val consignmentResponse = gcs.Data(Option(GetConsignment(CurrentStatus(taStatus, None))))
    val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    if (dataString.nonEmpty) {
      wireMockServer.stubFor(post(urlEqualTo("/graphql"))
        .withRequestBody(containing("getConsignmentStatus"))
        .willReturn(okJson(dataString)))
    }

    setConsignmentTypeResponse(wireMockServer, consignmentType)
  }

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

  def getTransferAgreementForm(optionsType: String, numberOfValuesToRemove: Int=0): Seq[(String, String)] = {
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

    options(optionsType).dropRight(numberOfValuesToRemove)
  }

  def checkHtmlContentForErrorSummary(htmlAsString: String, optionType: String, optionsSelected: Set[String]): Unit = {

    val potentialErrorsOnPage = Map(
      "privateBeta" ->  Map(
        "publicRecord" -> "All records must be confirmed as public before proceeding",
        "crownCopyright" -> "All records must be confirmed Crown Copyright before proceeding",
        "english" -> "All records must be confirmed as English language before proceeding"
      ),
      "compliance" -> Map(
        "droAppraisalSelection" -> "Departmental Records Officer (DRO) must have signed off the appraisal and selection decision for records",
        "droSensitivity" -> "Departmental Records Officer (DRO) must have signed off sensitivity review",
        "openRecords" -> "All records must be open"
      )
    )

    val errorsThatShouldBeOnPage: Map[String, String] = potentialErrorsOnPage(optionType).filter {
      case (errorName, _) => !optionsSelected.contains(errorName)
    }

    errorsThatShouldBeOnPage.values.foreach(error => htmlAsString must include(error))
  }

  def instantiateTransferAgreementPrivateBetaController(securityComponents: SecurityComponents,
                                                        config: Configuration,
                                                        keycloakConfiguration: KeycloakConfiguration =
                                                        getValidKeycloakConfiguration): TransferAgreementPrivateBetaController = {

    val graphQLConfiguration = new GraphQLConfiguration(config)
    val transferAgreementService = new TransferAgreementService(graphQLConfiguration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)

    new TransferAgreementPrivateBetaController(securityComponents, new GraphQLConfiguration(config),
      transferAgreementService, keycloakConfiguration, consignmentService)
  }

  def instantiateTransferAgreementComplianceController(securityComponents: SecurityComponents,
                                                       config: Configuration,
                                                       keycloakConfiguration: KeycloakConfiguration =
                                                       getValidKeycloakConfiguration): TransferAgreementComplianceController = {
    val graphQLConfiguration = new GraphQLConfiguration(config)
    val transferAgreementService = new TransferAgreementService(graphQLConfiguration)
    val consignmentService = new ConsignmentService(graphQLConfiguration)

    new TransferAgreementComplianceController(securityComponents, new GraphQLConfiguration(config),
      transferAgreementService, keycloakConfiguration, consignmentService)
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

}
