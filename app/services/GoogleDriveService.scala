package services

import io.circe.syntax._
import io.circe.generic.auto._
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.{AccessToken, GoogleCredentials}
import com.typesafe.config.ConfigFactory
import configuration.KeycloakConfiguration
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import org.keycloak.common.util.{Base64Url, KeycloakUriBuilder}
import org.pac4j.core.profile.UserProfile
import play.api.libs.json.Json
import play.api.libs.ws.WSClient
import uk.gov.nationalarchives.tdr.keycloak.{KeycloakUtils, TdrKeycloakDeployment}

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.{LocalDateTime, ZoneOffset}
import java.util.{Date, UUID}
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import play.api.libs.ws.JsonBodyWritables._
import services.GoogleDriveService.DriveTransferEvent
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.http.apache.ApacheHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.lambda.{LambdaAsyncClient, LambdaClient}
import software.amazon.awssdk.services.lambda.model.{InvocationType, InvokeRequest}

import scala.jdk.FutureConverters.CompletionStageOps

class GoogleDriveService @Inject()(val ws: WSClient,
                                   implicit val keycloakConfiguration: KeycloakConfiguration,
                                   implicit val executionContext: ExecutionContext) {

  case class MetadataItems(items: List[Metadata])
  case class Metadata(path: String, name: String, size: Int, isDir: Boolean, hash: String, lastModified: String)

  def getFileMetadata(accessToken: String, parentFolder: String): Future[List[Metadata]] = {
    val data = Json.obj("token" -> accessToken, "parentFolder" -> parentFolder)
    ws.url("https://pwn1mo37jk.execute-api.eu-west-2.amazonaws.com/metadata")
      .post(data)
      .map(res => {
        decode[MetadataItems](res.body) match {
          case Left(err) =>
            throw err
          case Right(value) =>
            value
        }
      }.items.filter(!_.isDir))
  }

  def getAccountUrl(profileOpt: Option[UserProfile], redirectPath: String): Option[String] = {
    profileOpt.flatMap(profile => {
      val tokenString = profile.getAttribute("access_token").toString
      keycloakConfiguration.token(tokenString)
    }).map(token => {
      val clientId: String = token.getIssuedFor
      val nonce = UUID.randomUUID().toString
      val md: MessageDigest = MessageDigest.getInstance("SHA-256")
      val provider = "google"

      val input = nonce + token.getSessionState + clientId + provider;
      val check = md.digest(input.getBytes(StandardCharsets.UTF_8));
      val hash = Base64Url.encode(check);
      val realm = "tdr"
      KeycloakUriBuilder.fromUri(s"http://localhost:8081")
        .path("realms/tdr/broker/microsoft/link")
        .queryParam("nonce", nonce)
        .queryParam("hash", hash)
        .queryParam("client_id", clientId)
        .queryParam("redirect_uri", s"http://localhost:9000/$redirectPath").build(realm, provider).toString
    })
  }

  case class Token(access_token: String)

  def getGoogleToken(accessToken: String): Future[String] = {
    val config = ConfigFactory.load
    ws.url("http://localhost:8081/realms/tdr/protocol/openid-connect/token")
      .post(Map(
        "client_id" -> Seq("tdr"),
        "client_secret" -> Seq(config.getString("auth.secret")),
        "subject_token" -> Seq(accessToken),
        "audience" -> Seq("https://www.googleapis.com/auth/drive"),
        "requested_issuer" -> Seq("google"),
        "grant_type" -> Seq("urn:ietf:params:oauth:grant-type:token-exchange")
      )).map(res => {
      //Check for errors and redirect to the upload page if link has expired
      decode[Token](res.body) match {
        case Left(err) => throw err
        case Right(value) => value
      }
    }).map(_.access_token)
  }

  def getFiles(accessToken: String): List[File] = {
    val service: Drive = getDriveService(accessToken)
    service.files().list().execute().getFiles.asScala.toList
  }

  def invokeTransferLambda(driveTransferEvent: DriveTransferEvent): Future[Unit] = {
    val httpClient = NettyNioAsyncHttpClient.builder.build
    val client = LambdaAsyncClient.builder
      .region(Region.EU_WEST_2)
      .httpClient(httpClient)
      .build
    val payload = driveTransferEvent.asJson.printWith(Printer.noSpaces)
    val invokeRequest = InvokeRequest.builder
      .functionName("tdr-cloud-to-cloud-copy-intg")
      .payload(SdkBytes.fromUtf8String(payload))
      .invocationType(InvocationType.REQUEST_RESPONSE)
      .build
    val response = client.invoke(invokeRequest).asScala
    response.flatMap(r => Option(r.functionError()) match {
      case None => Future(())
      case Some(err) => Future.failed(new Exception(s"Bad response from lambda $err"))
    })
  }

  private def getDriveService(accessToken: String) = {
    val expiry = Date.from(LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.UTC))
    val credentials = GoogleCredentials.newBuilder.setAccessToken(new AccessToken(accessToken, expiry)).build
    val httpCredentials: HttpCredentialsAdapter = new HttpCredentialsAdapter(credentials)

    val transport = GoogleNetHttpTransport.newTrustedTransport()
    val jsonFactory = GsonFactory.getDefaultInstance
    val service = new Drive.Builder(transport, jsonFactory, httpCredentials).build()
    service
  }
}
object GoogleDriveService {
  case class DriveTransferEvent(token: String, bucket: String, driveParent: String, driveFolder: String, s3Path: String)
}
