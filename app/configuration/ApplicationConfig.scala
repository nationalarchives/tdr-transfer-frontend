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
    get("s3Upload.ifNoneMatchHeaderValue"),
    get("s3Upload.aclHeaderValue")
  )

  val numberOfItemsOnViewTransferPage: Int = configuration.get[Int]("viewTransfers.numberOfItemsPerPage")

  val s3Endpoint: String = configuration.get[String]("s3.endpoint")

  val snsEndpoint: String = configuration.get[String]("sns.endpoint")

  val stepFunctionEndpoint: String = configuration.get[String]("stepFunction.endpoint")

  val exportStepFunctionArn: String = configuration.get[String]("export.stepFunctionArn")

  val backendChecksStepFunctionArn: String = configuration.get[String]("backendchecks.stepFunctionArn")

  val metadataValidationStepFunctionArn: String = configuration.get[String]("metadatavalidation.stepFunctionArn")

  val seriesNameFilters: Seq[String] = configuration.get[Seq[String]]("seriesNameFilters")

  val blockSkipMetadataReview: Boolean = configuration.get[Boolean]("featureAccessBlock.blockSkipMetadataReview")

  val draft_metadata_s3_bucket_name: String = configuration.get[String]("draft_metadata_s3_bucket_name")

  val transferErrorsS3BucketName: String = configuration.get[String]("transfer_errors_s3_bucket_name")

  val draftMetadataFileName: String = configuration.get[String]("draftMetadata.fileName")

  val draftMetadataErrorFileName: String = configuration.get[String]("draftMetadata.errorFileName")

  val notificationSnsTopicArn: String = get("notificationSnsTopicArn")

  val fileChecksTotalTimeoutInSeconds: Int = configuration.get[Int]("fileChecksTotalTimeoutInSeconds")

  val maxNumberOfFiles: Int = configuration.get[Int]("capacityLimits.maxNumberRecords")

  val maxFileSizeMb: Int = configuration.get[Int]("capacityLimits.maxIndividualFileSizeMb")

  val maxTransferSizeMb: Int = configuration.get[Int]("capacityLimits.maxTransferSizeMb")

  val blockConnectorSharePointPages: Boolean = configuration.get[Boolean]("featureAccessBlock.blockConnectorSharePointPages")
}
