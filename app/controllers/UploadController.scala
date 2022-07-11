package controllers

import auth.TokenSecurity
import configuration.{FrontEndInfoConfiguration, GraphQLConfiguration, KeycloakConfiguration}
import graphql.codegen.types.{AddFileAndMetadataInput, ConsignmentStatusInput, StartUploadInput}
import io.circe.parser.decode
import io.circe.syntax._
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}
import services.{ConsignmentService, ConsignmentStatusService, UploadService}
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UploadController @Inject()(val controllerComponents: SecurityComponents,
                                 val graphqlConfiguration: GraphQLConfiguration,
                                 val keycloakConfiguration: KeycloakConfiguration,
                                 val frontEndInfoConfiguration: FrontEndInfoConfiguration,
                                 val consignmentService: ConsignmentService,
                                 val uploadService: UploadService)
                                (implicit val ec: ExecutionContext) extends TokenSecurity with I18nSupport {

  def updateConsignmentStatus(): Action[AnyContent] = secureAction.async { implicit request =>
    request.body.asJson.flatMap(body => {
      decode[ConsignmentStatusInput](body.toString).toOption
    }) match {
      case None => Future.failed(new Exception(s"Incorrect data provided ${request.body}"))
      case Some(input) => uploadService.updateConsignmentStatus(input, request.token.bearerAccessToken).map(_.toString).map(Ok(_))
    }
  }

  def startUpload(): Action[AnyContent] = secureAction.async { implicit request =>
    request.body.asJson.flatMap(body => {
      decode[StartUploadInput](body.toString).toOption
    }) match {
      case None => Future.failed(new Exception(s"Incorrect data provided ${request.body}"))
      case Some(input) => uploadService.startUpload(input, request.token.bearerAccessToken).map(Ok(_))
    }
  }

  def saveClientMetadata(): Action[AnyContent] = secureAction.async { implicit request =>
    request.body.asJson.flatMap(body => {
      decode[AddFileAndMetadataInput](body.toString()).toOption
    }) match {
      case Some(metadataInput) => uploadService.saveClientMetadata(metadataInput, request.token.bearerAccessToken).map(res => Ok(res.asJson.noSpaces))
      case None => Future.failed(new Exception(s"Incorrect data provided ${request.body}"))
    }
  }

  def uploadPage(consignmentId: UUID): Action[AnyContent] = standardTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

    for {
      consignmentStatus <- consignmentStatusService.getConsignmentStatus(consignmentId, request.token.bearerAccessToken)
      reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
    } yield {
      val transferAgreementStatus: Option[String] = consignmentStatus.flatMap(_.transferAgreement)
      val uploadStatus: Option[String] = consignmentStatus.flatMap(_.upload)
      val pageHeadingUpload = "Upload your records"
      val pageHeadingUploading = "Uploading records"

      transferAgreementStatus match {
        case Some("Completed") =>
          uploadStatus match {
            case Some("InProgress") =>
              Ok(views.html.uploadInProgress(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = false))
                .uncache()
            case Some("Completed") =>
              Ok(views.html.uploadHasCompleted(consignmentId, reference, pageHeadingUploading, request.token.name, isJudgmentUser = false))
                .uncache()
            case None =>
              Ok(views.html.standard.upload(consignmentId, reference, pageHeadingUpload, pageHeadingUploading,
                frontEndInfoConfiguration.frontEndInfo, request.token.name))
                .uncache()
            case _ =>
              throw new IllegalStateException(s"Unexpected Upload status: $uploadStatus for consignment $consignmentId")
          }
        case Some("InProgress") =>
          Redirect(routes.TransferAgreementComplianceController.transferAgreement(consignmentId))
        case None =>
          Redirect(routes.TransferAgreementPrivateBetaController.transferAgreement(consignmentId))
        case _ =>
          throw new IllegalStateException(s"Unexpected Transfer Agreement status: $transferAgreementStatus for consignment $consignmentId")
      }
    }
  }

  def judgmentUploadPage(consignmentId: UUID): Action[AnyContent] = judgmentTypeAction(consignmentId) {
    implicit request: Request[AnyContent] =>
      val consignmentStatusService = new ConsignmentStatusService(graphqlConfiguration)

      for {
        consignmentStatus <- consignmentStatusService.getConsignmentStatus(consignmentId, request.token.bearerAccessToken)
        reference <- consignmentService.getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      } yield {
        val uploadStatus: Option[String] = consignmentStatus.flatMap(_.upload)
        val pageHeading1stHalf = "Upload judgment"
        val pageHeading2ndHalf = "Uploading judgment"

        uploadStatus match {
          case Some("InProgress") =>
            Ok(views.html.uploadInProgress(consignmentId, reference, pageHeading2ndHalf, request.token.name, isJudgmentUser = true))
              .uncache()
          case Some("Completed") =>
            Ok(views.html.uploadHasCompleted(consignmentId, reference, pageHeading2ndHalf, request.token.name, isJudgmentUser = true))
              .uncache()
          case None =>
            Ok(views.html.judgment.judgmentUpload(consignmentId, reference, pageHeading1stHalf, pageHeading2ndHalf,
              frontEndInfoConfiguration.frontEndInfo, request.token.name)).uncache()
          case _ =>
            throw new IllegalStateException(s"Unexpected Upload status: $uploadStatus for consignment $consignmentId")
        }
      }
  }
}
