package configuration

import javax.inject.Inject
import play.api.Configuration
import viewsapi.FrontEndInfo

class ApplicationConfig @Inject() (configuration: Configuration) {

  private def get(location: String) = configuration.get[String](location)

  def frontEndInfo: FrontEndInfo = FrontEndInfo(
    get("consignmentapi.url"),
    get("environment"),
    get("region"),
    get("upload.url")
  )

  val numberOfItemsOnViewTransferPage: Int = configuration.get[Int]("viewTransfers.numberOfItemsPerPage")

  val blockDraftMetadataUpload: Boolean = configuration.get[Boolean]("featureAccessBlock.blockDraftMetadataUpload")

  val metadataValidationBaseUrl: String = configuration.get[String]("metadatavalidation.baseUrl")

  val draft_metadata_s3_bucket_name: String = configuration.get[String]("draft_metadata_s3_bucket_name")

}
