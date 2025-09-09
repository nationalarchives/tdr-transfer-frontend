package controllers

import auth.TokenSecurity
import configuration.{ApplicationConfig, GraphQLConfiguration, KeycloakConfiguration}
import org.pac4j.play.scala.SecurityComponents
import play.api.i18n.I18nSupport
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, AnyContent, Request, Result}
import services.ConsignmentService
import viewsapi.Caching.preventCaching

import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SelectDocumentTypeController @Inject() (
    val controllerComponents: SecurityComponents,
    val graphqlConfiguration: GraphQLConfiguration,
    val keycloakConfiguration: KeycloakConfiguration,
    val consignmentService: ConsignmentService,
    val applicationConfig: ApplicationConfig
)(implicit val ec: ExecutionContext)
    extends TokenSecurity
    with I18nSupport {

  val selectedDocumentTypeForm: Form[SelectedDocumentTypeData] = Form(
    mapping(
      "documentType" -> optional(text).verifying("Select a document type", t => t.nonEmpty)
    )(SelectedDocumentTypeData.apply)(SelectedDocumentTypeData.unapply)
  )

  val typeFormNamesAndLabels: Seq[(String, String)] = Seq(
    ("original", "It's a judgment or decision being transferred for the first time"),
    ("update", "It's an update to a judgment or decision "),
    ("press-summary", "It's a press summary related to a judgment or decision ")
  )

  val reasonFormNamesAndLabels: Seq[(String, String)] = Seq(
    ("typo", "Typo"),
    ("Formatting", "Formatting"),
    ("NCN", "Amendment to NCN"),
    ("Anonymisation", "Anonymisation/redaction"),
    ("Other", "Other")
  )


  def selectDocumentType(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    consignmentService
      .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
      .map(reference =>
        Ok(views.html.judgment.judgmentDocumentType(consignmentId, reference, request.token.name, selectedDocumentTypeForm, typeFormNamesAndLabels, reasonFormNamesAndLabels, applicationConfig.blockJudgmentPressSummaries))
      )
  }

  def submitSelectedDocumentType(consignmentId: UUID): Action[AnyContent] = judgmentUserAndTypeAction(consignmentId) { implicit request: Request[AnyContent] =>
    {
      val formValidationResult: Form[SelectedDocumentTypeData] = selectedDocumentTypeForm.bindFromRequest()

      val errorFunction: Form[SelectedDocumentTypeData] => Future[Result] = { formWithErrors: Form[SelectedDocumentTypeData] =>
        consignmentService
          .getConsignmentRef(consignmentId, request.token.bearerAccessToken)
          .map(reference =>
            BadRequest(views.html.judgment.judgmentDocumentType(consignmentId, reference, request.token.name, formWithErrors, typeFormNamesAndLabels, reasonFormNamesAndLabels, applicationConfig.blockJudgmentPressSummaries))
          )

        // BadRequest(views.html.judgment.judgmentDocumentType(consignmentId, "", request.token.name, applicationConfig.blockJudgmentPressSummaries, formWithErrors))
      }

      val successFunction: SelectedDocumentTypeData => Future[Result] = { formData: SelectedDocumentTypeData =>
        formData.documentType match {
          case Some("original") => Future.successful(Redirect(routes.BeforeUploadingController.beforeUploading(consignmentId))) // TODO Update judgment type
          case Some("update")   => Future(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = true))) // TODO Redirect to Provide NCN Page
          case Some("press-summary") =>
            Future(Ok(views.html.notFoundError(name = request.token.name, isLoggedIn = true, isJudgmentUser = true))) // TODO Redirect to Provide NCN Page
          case _ => Future.successful(Redirect(routes.SelectDocumentTypeController.selectDocumentType(consignmentId)))
        }
      }

      formValidationResult.fold(
        errorFunction,
        successFunction
      )
    }
  }
}

case class SelectedDocumentTypeData(documentType: Option[String])
