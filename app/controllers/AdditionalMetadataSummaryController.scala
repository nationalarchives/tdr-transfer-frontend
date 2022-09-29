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
    val fileFilters = None
    for {
      consignment <- consignmentService.getConsignmentFileMetadata(consignmentId, request.token.bearerAccessToken, fileFilters)
      response <- consignment.files match {
        case first :: _ =>
          Future(Ok(views.html.standard.additionalMetadataSummary(fileName, consignment.consignmentReference,
            getMetadataForView(first.metadata), request.token.name)))
        case Nil => Future.failed(new IllegalStateException(s"Can't find selected files for the consignment $consignmentId"))
      }
    } yield response
  }

  private def getMetadataForView(metaData: GetConsignment.Files.Metadata): Metadata = {

    val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    Metadata(
      metaData.foiExemptionAsserted.map(_.format(formatter)).getOrElse(""),
      metaData.closureStartDate.map(_.format(formatter)).getOrElse(""),
      metaData.foiExemptionCode.getOrElse(""),
      metaData.closurePeriod.map(_.toString).getOrElse("0")
    )
  }
}

case class Metadata(foiExemptionAsserted: String,
                    closureStartDate: String,
                    foiExemptionCode: String,
                    closurePeriod: String)
