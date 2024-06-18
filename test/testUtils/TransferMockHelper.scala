package testUtils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{equalToJson, okJson, post, urlEqualTo}
import configuration.GraphQLConfiguration
import graphql.codegen.AddFinalTransferConfirmation.{addFinalTransferConfirmation => aftc}
import graphql.codegen.UpdateTransferInitiated.{updateTransferInitiated => ut}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.syntax._
import play.api.Application

import java.util.UUID
import scala.concurrent.ExecutionContext

object TransferMockHelper {

  def stubUpdateTransferInitiatedResponse(wiremockServer: WireMockServer, consignmentId: UUID)(implicit app: Application, ec: ExecutionContext): Any = {
    val utClient = new GraphQLConfiguration(app.configuration).getClient[ut.Data, ut.Variables]()
    val utData: utClient.GraphqlData = utClient.GraphqlData(Some(ut.Data(Option(1))), List())
    val utDataString: String = utData.asJson.printWith(Printer(dropNullValues = false, ""))

    val utQuery: String =
      s"""{"query":"mutation updateTransferInitiated($$consignmentId:UUID!)
         |                 {updateTransferInitiated(consignmentid:$$consignmentId)}",
         | "variables":{"consignmentId": "${consignmentId.toString}"}
         |}""".stripMargin.replaceAll("\n\\s*", "")

    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(equalToJson(utQuery))
        .willReturn(okJson(utDataString))
    )
  }

  def stubFinalTransferConfirmationResponse(wiremockServer: WireMockServer, consignmentId: UUID)(implicit app: Application, ec: ExecutionContext): Unit = {

    val addFinalTransferConfirmationResponse = Some(
      new aftc.AddFinalTransferConfirmation(
        consignmentId,
        legalCustodyTransferConfirmed = true
      )
    )
    val client = new GraphQLConfiguration(app.configuration).getClient[aftc.Data, aftc.Variables]()

    val data: client.GraphqlData = client.GraphqlData(addFinalTransferConfirmationResponse.map(ftc => aftc.Data(ftc)), Nil)
    val dataString: String = data.asJson.printWith(Printer(dropNullValues = false, ""))
    val query =
      s"""{"query":"mutation addFinalTransferConfirmation($$input:AddFinalTransferConfirmationInput!)
                            {addFinalTransferConfirmation(addFinalTransferConfirmationInput:$$input)
                            {consignmentId legalCustodyTransferConfirmed}}",
           "variables":{
                        "input":{
                                 "consignmentId":"$consignmentId",
                                 "legalCustodyTransferConfirmed":true
                                }
                       }
                             }""".stripMargin.replaceAll("\n\\s*", "")
    wiremockServer.stubFor(
      post(urlEqualTo("/graphql"))
        .withRequestBody(equalToJson(query))
        .willReturn(okJson(dataString))
    )
  }
}
