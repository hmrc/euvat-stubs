package uk.gov.hmrc.euvatstubs.models.responses

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class AddAttachmentResponseSpec extends AnyWordSpec with Matchers {

  private val response = AddAttachmentResponse(
    attachmentNumber     = 1,
    updateSequenceNumber = 11
  )

  "AddAttachmentResponse JSON format" should {

    "serialize and deserialize correctly" in {
      Json.toJson(response).as[AddAttachmentResponse] shouldBe response
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "attachmentNumber"     -> 1,
        "updateSequenceNumber" -> 11
      )

      json.as[AddAttachmentResponse] shouldBe
        AddAttachmentResponse(
          attachmentNumber     = 1,
          updateSequenceNumber = 11
        )
    }

    "fail to deserialize when attachmentNumber is missing" in {
      val json = Json.obj(
        "updateSequenceNumber" -> 11
      )

      json.validate[AddAttachmentResponse].isError shouldBe true
    }

    "fail to deserialize when updateSequenceNumber is missing" in {
      val json = Json.obj(
        "attachmentNumber" -> 1
      )

      json.validate[AddAttachmentResponse].isError shouldBe true
    }
  }
}
