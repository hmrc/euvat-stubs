/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    fileSize             = fileContent.length,
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
        "fileSize"             -> 5,
        "fileContent"          -> Json.arr(1, 2, 3, 4, 5),
        "updateSequenceNumber" -> 10
      )

      val result =
        json.as[AddAttachmentRequest]

      result.applicationId        shouldBe 123456L
      result.fileName             shouldBe "receipt.pdf"
      result.fileType             shouldBe "pdf"
      result.fileDescription      shouldBe Some("Purchase receipt")
      result.fileSize             shouldBe 5
      result.fileContent.toSeq    shouldBe Seq[Byte](1, 2, 3, 4, 5)
      result.updateSequenceNumber shouldBe 10
    }

    "deserialize correctly when fileDescription is not provided" in {
      val json = Json.obj(
        "applicationId"        -> 123456L,
        "fileName"             -> "receipt.pdf",
        "fileType"             -> "pdf",
        "fileSize"             -> 5,
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
        "fileSize"             -> 5,
        "fileContent"          -> Json.arr(1, 2, 3, 4, 5),
        "updateSequenceNumber" -> 10
      )

      json.validate[AddAttachmentRequest].isError shouldBe true
    }
  }
}
