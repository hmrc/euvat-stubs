package uk.gov.hmrc.euvatstubs.models.requests

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class AddAttachmentRequestSpec extends AnyWordSpec with Matchers {

  private val fileContent: Array[Byte] =
    Array[Byte](1, 2, 3, 4, 5)

  private val request = AddAttachmentRequest(
    applicationId        = 123456L,
    fileName             = "receipt.pdf",
    fileType             = "pdf",
    fileDescription      = Some("Purchase receipt"),
    fileSize             = fileContent.length.toLong,
    fileContent          = fileContent,
    updateSequenceNumber = 10
  )

  "AddAttachmentRequest JSON format" should {

    "serialize and deserialize correctly" in {
      val result =
        Json.toJson(request).as[AddAttachmentRequest]

      result.applicationId        shouldBe request.applicationId
      result.fileName             shouldBe request.fileName
      result.fileType             shouldBe request.fileType
      result.fileDescription      shouldBe request.fileDescription
      result.fileSize             shouldBe request.fileSize
      result.fileContent.toSeq    shouldBe request.fileContent.toSeq
      result.updateSequenceNumber shouldBe request.updateSequenceNumber
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "applicationId"        -> 123456L,
        "fileName"             -> "receipt.pdf",
        "fileType"             -> "pdf",
        "fileDescription"      -> "Purchase receipt",
        "fileSize"             -> 5L,
        "fileContent"          -> Json.arr(1, 2, 3, 4, 5),
        "updateSequenceNumber" -> 10
      )

      val result =
        json.as[AddAttachmentRequest]

      result.applicationId        shouldBe 123456L
      result.fileName             shouldBe "receipt.pdf"
      result.fileType             shouldBe "pdf"
      result.fileDescription      shouldBe Some("Purchase receipt")
      result.fileSize             shouldBe 5L
      result.fileContent.toSeq    shouldBe Seq[Byte](1, 2, 3, 4, 5)
      result.updateSequenceNumber shouldBe 10
    }

    "deserialize correctly when fileDescription is not provided" in {
      val json = Json.obj(
        "applicationId"        -> 123456L,
        "fileName"             -> "receipt.pdf",
        "fileType"             -> "pdf",
        "fileSize"             -> 5L,
        "fileContent"          -> Json.arr(1, 2, 3, 4, 5),
        "updateSequenceNumber" -> 10
      )

      val result =
        json.as[AddAttachmentRequest]

      result.fileDescription shouldBe None
    }

    "fail to deserialize when a mandatory field is missing" in {
      val json = Json.obj(
        "fileName"             -> "receipt.pdf",
        "fileType"             -> "pdf",
        "fileDescription"      -> "Purchase receipt",
        "fileSize"             -> 5L,
        "fileContent"          -> Json.arr(1, 2, 3, 4, 5),
        "updateSequenceNumber" -> 10
      )

      json.validate[AddAttachmentRequest].isError shouldBe true
    }
  }
}
