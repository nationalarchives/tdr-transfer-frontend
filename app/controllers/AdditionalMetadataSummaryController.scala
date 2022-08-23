package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import graphql.codegen.GetConsignmentFilesMetadata.getConsignmentFilesMetadata.GetConsignment
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService

import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject
import scala.concurrent.Future

class AdditionalMetadataSummaryController @Inject ()(val consignmentService: ConsignmentService,
                                                     val keycloakConfiguration: KeycloakConfiguration,
                                                     val controllerComponents: SecurityComponents
                                              ) extends TokenSecurity {

  def getSelectedSummaryPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
//  TODO:  Get fileName and selectedFileIds from previous page
    val fileName = "Flour.txt"
    val selectedFileIds = None
    for {
      consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, selectedFileIds)
      response <- consignment.files match {
        case first :: _ =>
          Future(Ok(views.html.standard.additionalMetadataSummary(fileName, consignment.consignmentReference, getMetadata(first.metadata), request.token.name)))
        case Nil => Future.failed(new IllegalStateException(s"Can't find selected files for the consignment $consignmentId"))
      }
    } yield response
  }

  private def getMetadata(metaData: GetConsignment.Files.Metadata): Map[String, String] = {

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    Map(
      "foiExemptionAsserted" -> metaData.foiExemptionAsserted.map(_.format(formatter)).getOrElse(""),
      "closureStartDate" -> metaData.closureStartDate.map(_.format(formatter)).getOrElse(""),
      "foiExemptionCode" -> metaData.foiExemptionCode.getOrElse(""),
      "closurePeriod" -> metaData.closurePeriod.map(_.toString).getOrElse("0")
    )
  }
}
