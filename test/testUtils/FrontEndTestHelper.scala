package testUtils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{containing, okJson, post, urlEqualTo}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import com.nimbusds.openid.connect.sdk.op.OIDCProviderMetadata
import configuration.{FrontEndInfoConfiguration, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.AddBulkFileMetadata.addBulkFileMetadata.UpdateBulkFileMetadata
import graphql.codegen.AddBulkFileMetadata.{addBulkFileMetadata => abfm}
import graphql.codegen.DeleteFileMetadata.deleteFileMetadata.DeleteFileMetadata
import graphql.codegen.GetAllDescendants.getAllDescendantIds
import graphql.codegen.GetAllDescendants.getAllDescendantIds.AllDescendants
import graphql.codegen.GetConsignmentFilesMetadata.{getConsignmentFilesMetadata => gcfm}
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment
import graphql.codegen.GetConsignmentStatus.getConsignmentStatus.GetConsignment.{CurrentStatus, Series}
import graphql.codegen.GetConsignmentStatus.{getConsignmentStatus => gcs}
import graphql.codegen.DeleteFileMetadata.{deleteFileMetadata => dfm}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import org.keycloak.representations.AccessToken
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.pac4j.core.client.Clients
import org.pac4j.core.config.Config
import org.pac4j.core.context.session.SessionStore
import org.pac4j.core.engine.DefaultSecurityLogic
import org.pac4j.core.http.ajax.AjaxRequestResolver
import org.pac4j.core.util.Pac4jConstants
import org.pac4j.oidc.client.OidcClient
import org.pac4j.oidc.config.OidcConfiguration
import org.pac4j.oidc.profile.{OidcProfile, OidcProfileDefinition}
import org.pac4j.oidc.redirect.OidcRedirectionActionBuilder
import org.pac4j.play.PlayWebContext
import org.pac4j.play.http.PlayHttpActionAdapter
import org.pac4j.play.scala.SecurityComponents
import org.pac4j.play.store.PlayCacheSessionStore
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures.{PatienceConfig, scaled}
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor1, TableFor2}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.{BodyParsers, ControllerComponents}
import play.api.test.Helpers.stubControllerComponents
import play.api.test.Injecting
import play.api.{Application, Configuration}
import uk.gov.nationalarchives.tdr.keycloak.Token
import viewsapi.FrontEndInfo

import java.io.File
import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import java.util.{Date, UUID}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.existentials


trait FrontEndTestHelper extends PlaySpec with MockitoSugar with Injecting with GuiceOneAppPerTest with BeforeAndAfterEach with TableDrivenPropertyChecks {

  implicit val patienceConfig: PatienceConfig = PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(100, Millis)))

  implicit class AwaitFuture[T](future: Future[T]) {
    def await(timeout: Duration = 2.seconds): T = {
      Await.result(future, timeout)
    }
  }

  def setConsignmentTypeResponse(wiremockServer: WireMockServer, consignmentType: String): StubMapping = {
    val dataString = s"""{"data": {"getConsignment": {"consignmentType": "$consignmentType"}}}"""
    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getConsignmentType"))
      .willReturn(okJson(dataString)))
  }

  def setConsignmentReferenceResponse(wiremockServer: WireMockServer, consignmentRef: String = "TEST-TDR-2021-GB"): StubMapping = {
    val dataString = s"""{"data": {"getConsignment": {"consignmentReference": "$consignmentRef"}}}"""
    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getConsignmentReference"))
      .willReturn(okJson(dataString)))
  }

  def setBulkUpdateMetadataResponse(wiremockServer: WireMockServer): StubMapping = {
    val client = new GraphQLConfiguration(app.configuration).getClient[abfm.Data, abfm.Variables]()
    val dataString = client.GraphqlData(Option(abfm.Data(UpdateBulkFileMetadata(Nil, Nil)))).asJson.printWith(Printer.noSpaces)
    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("updateBulkFileMetadata"))
      .willReturn(okJson(dataString)))
  }

  def setConsignmentFilesMetadataResponse(wiremockServer: WireMockServer, consignmentRef: String = "TEST-TDR-2021-GB",
                                          fileHasMetadata: Boolean = true, fileIds: List[UUID] = Nil, closureType: String = "Open"): StubMapping = {

    val client = new GraphQLConfiguration(app.configuration).getClient[gcfm.Data, gcfm.Variables]()
    val closureStartDate = LocalDateTime.of(1990, 12, 1, 10, 0)
    val foiExampleAsserted = LocalDateTime.of(1995, 1, 12, 10, 0)
    val (fileMetadata, metadata) = if (fileHasMetadata) {
      (List(
        gcfm.GetConsignment.Files.FileMetadata("ClosureType", closureType),
        gcfm.GetConsignment.Files.FileMetadata("FoiExemptionCode", "mock code1"),
        gcfm.GetConsignment.Files.FileMetadata("ClosurePeriod", "4"),
        gcfm.GetConsignment.Files.FileMetadata("ClosureStartDate", closureStartDate.format(DateTimeFormatter.ISO_DATE_TIME).replace("T", " ")),
        gcfm.GetConsignment.Files.FileMetadata("FoiExemptionAsserted", foiExampleAsserted.format(DateTimeFormatter.ISO_DATE_TIME).replace("T", " ")),
        gcfm.GetConsignment.Files.FileMetadata("TitleClosed", "false"),
        gcfm.GetConsignment.Files.FileMetadata("ClientSideOriginalFilepath", "original/file/path")
      ), gcfm.GetConsignment.Files.Metadata(Some("mock code1"), Some(4), Some(closureStartDate), Some(foiExampleAsserted), None, None))
    } else {
      (Nil, gcfm.GetConsignment.Files.Metadata(None, None, None, None, None, None))
    }
    val consignmentFilesMetadata = gcfm.Data(Option(gcfm.GetConsignment(
      fileIds.map(fileId =>
        gcfm.GetConsignment.Files(
          fileId,
          fileMetadata,
          metadata
        )), consignmentRef))
    )
    val data: client.GraphqlData = client.GraphqlData(Some(consignmentFilesMetadata))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getConsignmentFilesMetadata"))
      .willReturn(okJson(dataString)))
  }

  def setConsignmentDetailsResponse(
                                     wiremockServer: WireMockServer,
                                     parentFolder: Option[String],
                                     consignmentReference: String = "TEST-TDR-2021-GB",
                                     parentFolderId: Option[UUID]): StubMapping = {
    val folderOrNull = parentFolder.map(folder => s""" "$folder" """).getOrElse("null")
    val folderIdOrNull = parentFolderId.map(id => s""" "$id" """).getOrElse("null")
    val dataString =
      s"""{"data": {"getConsignment": {
         |"consignmentReference": "$consignmentReference",
         | "parentFolder": $folderOrNull,
         | "parentFolderId": $folderIdOrNull,
         | "userid" : "${UUID.randomUUID()}",
         | "seriesid": "${UUID.randomUUID()}"}}} """.stripMargin

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getConsignment($consignmentId:UUID!)"))
      .willReturn(okJson(dataString)))
  }

  def setConsignmentStatusResponse(config: Configuration,
                                   wiremockServer: WireMockServer,
                                   seriesId: Option[UUID] = None,
                                   seriesStatus: Option[String] = None,
                                   transferAgreementStatus: Option[String] = None,
                                   uploadStatus: Option[String] = None,
                                   confirmTransferStatus: Option[String] = None,
                                   exportStatus: Option[String] = None): StubMapping = {
    val client = new GraphQLConfiguration(config).getClient[gcs.Data, gcs.Variables]()
    val consignmentResponse = gcs.Data(Option(GetConsignment(
      Some(Series(seriesId.getOrElse(UUID.randomUUID()), "MOCK1")),
      CurrentStatus(seriesStatus, transferAgreementStatus, uploadStatus, confirmTransferStatus, exportStatus))))
    val data: client.GraphqlData = client.GraphqlData(Some(consignmentResponse))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("getConsignmentStatus"))
      .willReturn(okJson(dataString)))
  }

  def setDeleteFileMetadataResponse(config: Configuration,
                                    wiremockServer: WireMockServer,
                                    fileIds: List[UUID] = List(),
                                    filePropertyNames: List[String] = List()): StubMapping = {
    val client = new GraphQLConfiguration(config).getClient[dfm.Data, dfm.Variables]()
    val deleteFileMetadataResponse = dfm.Data(DeleteFileMetadata(fileIds, filePropertyNames))
    val data: client.GraphqlData = client.GraphqlData(Some(deleteFileMetadataResponse))
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .withRequestBody(containing("deleteFileMetadata"))
      .willReturn(okJson(dataString)))
  }

  def setAllDescendantIdsResponse(wiremockServer: WireMockServer,
                                  selectedDescendants: List[UUID] = List(),
                                  deselectedDescendants: List[UUID] = List(),
                                  folderDescendants: List[UUID] = List()): StubMapping = {
    val client = new GraphQLConfiguration(app.configuration).getClient[getAllDescendantIds.Data, getAllDescendantIds.Variables]()

    val selectedResponse = new getAllDescendantIds.Data(selectedDescendants.map(id => AllDescendants(id, Some("File"))))
    val selectedData: client.GraphqlData = client.GraphqlData(Some(selectedResponse))
    val selectedDataString: String = selectedData.asJson.printWith(Printer(dropNullValues = false, ""))

    val deselectedResponse = new getAllDescendantIds.Data(deselectedDescendants.map(id => AllDescendants(id, Some("File"))))
    val deselectedData: client.GraphqlData = client.GraphqlData(Some(deselectedResponse))
    val deselectedDataString: String = deselectedData.asJson.printWith(Printer(dropNullValues = false, ""))

    val folderResponse = new getAllDescendantIds.Data(folderDescendants.map(id => AllDescendants(id, Some("File"))))
    val folderData: client.GraphqlData = client.GraphqlData(Some(folderResponse))
    val folderDataString: String = folderData.asJson.printWith(Printer(dropNullValues = false, ""))

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .inScenario("allDescendants")
      .withRequestBody(containing("getAllDescendantIds"))
      .willReturn(okJson(selectedDataString))
      .willSetStateTo("selectedDescendants")
    )

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .inScenario("allDescendants")
      .whenScenarioStateIs("selectedDescendants")
      .withRequestBody(containing("getAllDescendantIds"))
      .willReturn(okJson(deselectedDataString))
      .willSetStateTo("deselectedDescendants")
    )

    wiremockServer.stubFor(post(urlEqualTo("/graphql"))
      .inScenario("allDescendants")
      .whenScenarioStateIs("deselectedDescendants")
      .withRequestBody(containing("getAllDescendantIds"))
      .willReturn(okJson(folderDataString))
      .willSetStateTo("folderDescendants")
    )
  }

  val userTypes: TableFor1[String] = Table(
    "User type",
    "judgment",
    "standard"
  )

  val metadataType: TableFor1[String] = Table(
    "Metadata Type",
    "closure",
    "descriptive"
  )

  val userChecks: TableFor2[KeycloakConfiguration, String] = Table(
    ("user", "url"),
    (getValidJudgmentUserKeycloakConfiguration, "consignment"),
    (getValidStandardUserKeycloakConfiguration, "judgment")
  )

  val consignmentStatuses: TableFor1[String] = Table(
    "Export status",
    "InProgress",
    "Completed",
    "Failed"
  )

  def frontEndInfoConfiguration: FrontEndInfoConfiguration = {
    val frontEndInfoConfiguration: FrontEndInfoConfiguration = mock[FrontEndInfoConfiguration]
    when(frontEndInfoConfiguration.frontEndInfo).thenReturn(
      FrontEndInfo(
        "https://mock-api-url.com/graphql",
        "mockStage",
        "mockRegion",
        "https://mock-upload-url.com"
      ))
    frontEndInfoConfiguration
  }

  override def fakeApplication(): Application = {
    val syncCacheApi = mock[PlayCacheSessionStore]
    GuiceApplicationBuilder()
      .in(new File("src/test/resources/application.conf"))
      .bindings(bind[PlayCacheSessionStore].toInstance(syncCacheApi))
      .build()
  }

  def getValidKeycloakConfiguration: KeycloakConfiguration = {
    val keycloakMock = mock[KeycloakConfiguration]
    val accessToken = new AccessToken()
    accessToken.setOtherClaims("body", "Body")
    accessToken.setOtherClaims("user_id", "c140d49c-93d0-4345-8d71-c97ff28b947e")
    val token = Token(accessToken, new BearerAccessToken)
    doAnswer(_ => Some(token)).when(keycloakMock).token(any[String])
    keycloakMock
  }

  def getValidStandardUserKeycloakConfiguration: KeycloakConfiguration = {
    val keycloakMock = mock[KeycloakConfiguration]
    val accessToken = new AccessToken()
    accessToken.setOtherClaims("body", "Body")
    accessToken.setOtherClaims("user_id", "c140d49c-93d0-4345-8d71-c97ff28b947e")
    accessToken.setOtherClaims("standard_user", "true")
    accessToken.setName("Standard Username")
    val token = Token(accessToken, new BearerAccessToken)
    doAnswer(_ => Some(token)).when(keycloakMock).token(any[String])
    keycloakMock
  }

  def getValidJudgmentUserKeycloakConfiguration: KeycloakConfiguration = {
    val keycloakMock = mock[KeycloakConfiguration]
    val accessToken = new AccessToken()
    accessToken.setOtherClaims("body", "Body")
    accessToken.setOtherClaims("user_id", "c140d49c-93d0-4345-8d71-c97ff28b947e")
    accessToken.setOtherClaims("judgment_user", "true")
    accessToken.setName("Judgment Username")
    val token = Token(accessToken, new BearerAccessToken)
    doAnswer(_ => Some(token)).when(keycloakMock).token(any[String])
    keycloakMock
  }

  def getValidKeycloakConfigurationWithoutBody: KeycloakConfiguration = {
    val keycloakMock = mock[KeycloakConfiguration]
    val accessToken = new AccessToken()
    val token = Token(accessToken, new BearerAccessToken)
    doAnswer(_ => Some(token)).when(keycloakMock).token(any[String])
    keycloakMock
  }

  def getInvalidKeycloakConfiguration: KeycloakConfiguration = {
    val keycloakMock = mock[KeycloakConfiguration]
    val accessToken = new AccessToken()
    accessToken.setOtherClaims("body", "Body")
    doAnswer(_ => Option.empty).when(keycloakMock).token(any[String])
    keycloakMock
  }

  def getAuthorisedSecurityComponents: SecurityComponents = {
    // Pac4j checks the session to see if there any profiles stored there. If there are, the request is authenticated.

    //Create the profile and add to the map
    val profile: OidcProfile = new OidcProfile()
    //This is the example token from jwt.io
    val jwtToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibm" +
      "FtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
    profile.setAccessToken(new BearerAccessToken(jwtToken))
    profile.addAttribute(OidcProfileDefinition.EXPIRATION, Date.from(LocalDateTime.now().plusDays(10).toInstant(ZoneOffset.UTC)))

    val profileMap: java.util.LinkedHashMap[String, OidcProfile] = new java.util.LinkedHashMap[String, OidcProfile]
    profileMap.put("OidcClient", profile)

    val playCacheSessionStore: SessionStore = mock[PlayCacheSessionStore]

    //Mock the get method to return the expected map.
    doAnswer(_ => java.util.Optional.of(profileMap)).when(playCacheSessionStore).get(
      any[PlayWebContext](), org.mockito.ArgumentMatchers.eq[String](Pac4jConstants.USER_PROFILES)
    )

    val testConfig = new Config()

    //Return true on the isAuthorized method
    val logic = DefaultSecurityLogic.INSTANCE
    logic.setAuthorizationChecker((_, _, _, _, _, _) => true)
    testConfig.setSecurityLogic(logic)

    //There is a null check for the action adaptor.
    testConfig.setHttpActionAdapter(new PlayHttpActionAdapter())

    // There is a check to see whether an OidcClient exists. The name matters and must match the string passed to Secure in the controller.
    val clients = new Clients()
    val configuration = mock[OidcConfiguration]
    doNothing().when(configuration).init()

    clients.setClients(new OidcClient(configuration))
    testConfig.setClients(clients)

    //Create a new controller with the session store and config. The parser and components don't affect the tests.
    securityComponents(testConfig, playCacheSessionStore)
  }

  private def securityComponents(testConfig: Config, playCacheSessionStore: SessionStore): SecurityComponents = new SecurityComponents {
    override def components: ControllerComponents = stubControllerComponents()

    override def config: Config = testConfig

    override def sessionStore: SessionStore = playCacheSessionStore

    //scalastyle:off null
    override def parser: BodyParsers.Default = null
    //scalastyle:on null
  }

  def getUnauthorisedSecurityComponents: SecurityComponents = {
    val testConfig = new Config()
    val logic = DefaultSecurityLogic.INSTANCE
    logic setAuthorizationChecker ((_, _, _, _, _, _) => false)
    testConfig.setSecurityLogic(logic)

    //There is a null check for the action adaptor.
    testConfig.setHttpActionAdapter(new PlayHttpActionAdapter())

    // There is a check to see whether an OidcClient exists. The name matters and must match the string passed to Secure in the controller.
    val clients = new Clients()
    val configuration = mock[OidcConfiguration]

    // Mock the init method to stop it calling out to the keycloak server
    doNothing().when(configuration).init()

    // Set some configuration parameters
    doReturn("tdr").when(configuration).getClientId
    doReturn("code").when(configuration).getResponseType
    doReturn(true).when(configuration).isUseNonce
    val providerMetadata = mock[OIDCProviderMetadata]
    doReturn(URI.create("/auth/realms/tdr/protocol/openid-connect/auth")).when(providerMetadata).getAuthorizationEndpointURI
    doReturn(providerMetadata).when(configuration).getProviderMetadata

    val resolver = mock[AjaxRequestResolver]
    doReturn(false).when(resolver).isAjax(any[PlayWebContext], any[SessionStore])

    // Create a concrete client
    val client = new OidcClient(configuration)
    client.setName("OidcClient")
    client.setAjaxRequestResolver(resolver)
    client.setRedirectionActionBuilder(new OidcRedirectionActionBuilder(client))
    client.setCallbackUrl("test")

    clients.setClients(client)
    testConfig.setClients(clients)

    //Create a new controller with the session store and config. The parser and components don't affect the tests.

    new SecurityComponents {
      override def components: ControllerComponents = stubControllerComponents()

      override def config: Config = testConfig

      override def sessionStore: SessionStore = mock[SessionStore]

      //scalastyle:off null
      override def parser: BodyParsers.Default = null
      //scalastyle:on null
    }
  }
}
