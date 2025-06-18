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
    get("upload.url"),
    get("auth.url"),
    get("auth.clientId"),
    get("auth.realm"),
    sys.env("AWS_ACCESS_KEY_ID"),
    sys.env("AWS_SECRET_ACCESS_KEY"),
    sys.env("AWS_SESSION_TOKEN"),
  )

  val numberOfItemsOnViewTransferPage: Int = configuration.get[Int]("viewTransfers.numberOfItemsPerPage")

  val metadataValidationBaseUrl: String = configuration.get[String]("metadatavalidation.baseUrl")

  val s3Endpoint: String = configuration.get[String]("s3.endpoint")

  val snsEndpoint: String = configuration.get[String]("sns.endpoint")

  val blockSkipMetadataReview: Boolean = configuration.get[Boolean]("featureAccessBlock.blockSkipMetadataReview")

  val draft_metadata_s3_bucket_name: String = configuration.get[String]("draft_metadata_s3_bucket_name")

  val draftMetadataFileName: String = configuration.get[String]("draftMetadata.fileName")

  val draftMetadataErrorFileName: String = configuration.get[String]("draftMetadata.errorFileName")

  val notificationSnsTopicArn: String = get("notificationSnsTopicArn")

  val fileChecksTotalTimoutInSeconds: Int = configuration.get[Int]("fileChecksTotalTimoutInSeconds")
}
