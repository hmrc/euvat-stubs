package uk.gov.hmrc.euvatstubs.models.responses

import play.api.libs.json.{Json, OFormat}

case class AddAttachmentResponse(
  attachmentNumber: Int,
  updateSequenceNumber: Int
)

object AddAttachmentResponse {
  implicit val format: OFormat[AddAttachmentResponse] =
    Json.format[AddAttachmentResponse]
}
