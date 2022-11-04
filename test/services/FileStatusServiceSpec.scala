package services

import com.nimbusds.oauth2.sdk.token.BearerAccessToken
import configuration.GraphQLBackend._
import configuration.GraphQLConfiguration
import graphql.codegen.AddFileStatus.addFileStatus.AddFileStatus
import graphql.codegen.AddFileStatus.{addFileStatus => afs}
import graphql.codegen.types.AddFileStatusInput
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers._
import org.scalatestplus.mockito.MockitoSugar.mock
import sttp.client.HttpError
import sttp.model.StatusCode
import uk.gov.nationalarchives.tdr.{GraphQLClient, GraphQlResponse}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class FileStatusServiceSpec extends AnyFlatSpec {
  implicit val ec: ExecutionContext = ExecutionContext.global
  private val graphQlConfig = mock[GraphQLConfiguration]
  private val token = new BearerAccessToken("some-token")

  "addFileStatus" should "add a file status" in {
    val input = AddFileStatusInput(UUID.randomUUID(), "Upload", "Success")
    val graphQlClientForAddFileStatus = mock[GraphQLClient[afs.Data, afs.Variables]]
    when(graphQlConfig.getClient[afs.Data, afs.Variables]())
      .thenReturn(graphQlClientForAddFileStatus)

    val graphQlResponse =
      GraphQlResponse(Some(afs.Data(AddFileStatus(input.fileId, "Upload", "Success"))), Nil)
    when(graphQlClientForAddFileStatus.getResult(token, afs.document, Some(afs.Variables(input))))
      .thenReturn(Future.successful(graphQlResponse))

    val response = new FileStatusService(graphQlConfig).addFileStatus(input, token).futureValue
    response.fileId should equal(input.fileId)
    response.statusType should equal(input.statusType)
    response.statusValue should equal(input.statusValue)
  }

  "addFileStatus" should "return an error when the API has an error" in {
    val input = AddFileStatusInput(UUID.randomUUID(), "Upload", "Success")
    val graphQlClientForAddFileStatus = mock[GraphQLClient[afs.Data, afs.Variables]]
    when(graphQlConfig.getClient[afs.Data, afs.Variables]())
      .thenReturn(graphQlClientForAddFileStatus)

    when(graphQlClientForAddFileStatus.getResult(token, afs.document, Some(afs.Variables(input))))
      .thenReturn(Future.failed(HttpError("something went wrong", StatusCode.InternalServerError)))

    val results = new FileStatusService(graphQlConfig).addFileStatus(input, token)
    results.failed.futureValue shouldBe a[HttpError]
  }
}
