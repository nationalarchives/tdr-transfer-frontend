package services

import cats.implicits.catsSyntaxOptionId
import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLConfiguration
import configuration.GraphQLBackend._
import org.scalatest.concurrent.ScalaFutures._
import org.mockito.Mockito.when
import org.scalatest.flatspec.AnyFlatSpec
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}
import graphql.codegen.StartUpload.{startUpload => su}
import graphql.codegen.AddFilesAndMetadata.{addFilesAndMetadata => afam}
import graphql.codegen.UpdateConsignmentStatus.{updateConsignmentStatus => ucs}
import graphql.codegen.types.{AddFileAndMetadataInput, ClientSideMetadataInput, ConsignmentStatusInput, StartUploadInput}
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatest.matchers.should.Matchers._

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class UploadServiceSpec extends AnyFlatSpec {
  implicit val ec: ExecutionContext = ExecutionContext.global
  private val graphQlConfig = mock[GraphQLConfiguration]
  private val token = new BearerAccessToken("some-token")

  "startUpload" should "return the correct data" in {
    val input = StartUploadInput(UUID.randomUUID(), "parent", Some(false))
    val graphQlClientForStartUpload = mock[GraphQLClient[su.Data, su.Variables]]
    when(graphQlConfig.getClient[su.Data, su.Variables]())
      .thenReturn(graphQlClientForStartUpload)

    val graphQlResponse =
      GraphQlResponse(Some(su.Data("ok")), Nil)
    when(graphQlClientForStartUpload.getResult(token, su.document, Some(su.Variables(input))))
      .thenReturn(Future.successful(graphQlResponse))

    val response = new UploadService(graphQlConfig).startUpload(input, token).futureValue
    response should equal("ok")
  }

  "saveMetadata" should "return the correct data" in {
    val graphQlClientForAddFilesAndMetadata = mock[GraphQLClient[afam.Data, afam.Variables]]
    when(graphQlConfig.getClient[afam.Data, afam.Variables]())
      .thenReturn(graphQlClientForAddFilesAndMetadata)

    val clientSideMetadataInput = ClientSideMetadataInput("originalPath", "checksum", 1, 1, 1) :: Nil
    val addFileAndMetadataInput: AddFileAndMetadataInput = AddFileAndMetadataInput(UUID.randomUUID(), clientSideMetadataInput, Some(Nil))
    val input = afam.AddFilesAndMetadata(UUID.randomUUID(), 0)

    val graphQlResponse =
      GraphQlResponse(Some(afam.Data(List(input))), Nil)
    when(graphQlClientForAddFilesAndMetadata.getResult(token, afam.document, Some(afam.Variables(addFileAndMetadataInput))))
      .thenReturn(Future.successful(graphQlResponse))

    val response = new UploadService(graphQlConfig).saveClientMetadata(addFileAndMetadataInput, token).futureValue
    response.size should equal(1)
    response.head should equal(input)
  }
}
