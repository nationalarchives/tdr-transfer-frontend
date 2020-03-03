package controllers

import javax.inject.{Inject, Singleton}
import org.pac4j.core.profile.CommonProfile
import org.pac4j.play.scala.{Security, SecurityComponents}
import play.api.data.Form
import play.api.data.Forms._
import play.api.i18n.I18nSupport
import play.api.mvc.{Action, AnyContent, Request}

@Singleton
class TransferAgreementController @Inject()(val controllerComponents: SecurityComponents) extends Security[CommonProfile] with I18nSupport {

  private val secureAction = Secure("OidcClient")
  private val options: Seq[(String, String)] = Seq("Yes" -> "true", "No" -> "false")

  val form = Form(
    mapping(
      "publicRecord" -> boolean
        .verifying("Must answer yes", b => b),
      "crownCopyright" -> boolean
        .verifying("Must answer yes", b => b),
      "english" -> boolean
        .verifying("Must answer yes", b => b),
      "digital" -> boolean
        .verifying("Must answer yes", b => b),
      "droAppraisalselection" -> boolean
        .verifying("Must answer yes", b => b),
      "droSensitivity" -> boolean
        .verifying("Must answer yes", b => b)
    )(TransferAgreementData.apply)(TransferAgreementData.unapply)
  )

  def transferAgreement(consignmentId: Long): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.transferAgreement(consignmentId, form, options))
  }

  def transferAgreementSubmit(consignmentId: Long): Action[AnyContent] = secureAction { implicit request: Request[AnyContent] =>
    Ok(views.html.transferAgreement(consignmentId, form, options))
  }
}

case class TransferAgreementData(publicRecord: Boolean,
                                 crownCopyright: Boolean,
                                 english: Boolean,
                                 digital: Boolean,
                                 droAppraisalselection: Boolean,
                                 droSensitivity: Boolean)
