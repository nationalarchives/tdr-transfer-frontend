package services

import akka.stream.alpakka.s3.{MultipartUploadResult, S3Headers}
import akka.stream.alpakka.s3.scaladsl.S3
import akka.stream.scaladsl.Sink
import akka.util.ByteString

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class AwsS3Client @Inject() () {

  val s3Headers = S3Headers.empty

  def s3Sink(bucket: String, key: String): Sink[ByteString, Future[MultipartUploadResult]] = {
    S3.multipartUploadWithHeaders(bucket, key, s3Headers = s3Headers)
    // S3.multipartUpload(bucket, key)
  }
}
