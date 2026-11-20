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
