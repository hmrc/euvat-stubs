package uk.gov.hmrc.euvatstubs.models.requests

import play.api.libs.json.{Json, OFormat}

case class AddAttachmentRequest(
  applicationId: Long,
  fileName: String,
  fileType: String,
  fileDescription: Option[String],
  fileSize: Long,
  fileContent: Array[Byte],
  updateSequenceNumber: Int
)

object AddAttachmentRequest {
  implicit val format: OFormat[AddAttachmentRequest] =
    Json.format[AddAttachmentRequest]
}
