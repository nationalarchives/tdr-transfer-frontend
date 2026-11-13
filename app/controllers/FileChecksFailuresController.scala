package controllers

import auth.TokenSecurity
import configuration.KeycloakConfiguration
import controllers.util.DateUtils
import org.pac4j.play.scala.SecurityComponents
import play.api.mvc.{Action, AnyContent, Request}
import services.ConsignmentService
import services.Statuses.UploadType

import java.util.UUID
import javax.inject.Inject

class FileChecksFailuresController @Inject() (
    val keycloakConfiguration: KeycloakConfiguration,
    val controllerComponents: SecurityComponents,
    val consignmentService: ConsignmentService
) extends TokenSecurity {

  def fileChecksFailures(consignmentId: UUID): Action[AnyContent] = tnaUserAction { implicit request: Request[AnyContent] =>
    for {
      consignments <- consignmentService.getConsignmentFileCheckFailures(Some(consignmentId), request.token.bearerAccessToken)
      consignment = consignments
        .find(_.consignmentid.contains(consignmentId))
        .getOrElse(throw new IllegalStateException(s"No file check failures found for consignment $consignmentId"))
      uploadDate = consignment.consignmentStatuses
        .find(_.statusType == UploadType.id)
        .map(_.createdDatetime)
        .getOrElse(throw new IllegalStateException(s"No upload status found for consignment $consignmentId"))
      userDetails <- keycloakConfiguration.userDetails(consignment.userid.toString)
    } yield {
      Ok(
        views.html.tna.fileChecksFailures(
          request.token.name,
          consignment.consignmentReference,
          consignment.transferringBodyName.getOrElse(""),
          consignment.seriesName.getOrElse(""),
          DateUtils.formatWithDaySuffix(uploadDate),
          userDetails.email
        )
      )
    }
  }
}
