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

class GetAttachmentDetailsResponseSpec extends AnyWordSpec with Matchers {

  private val fullResponse = GetAttachmentDetailsResponse(
    attachmentNumber     = 11,
    fileName             = Some("test.pdf"),
    fileType             = Some("pdf"),
    fileDescription      = Some("pdf file attached"),
    fileSize             = Some(42),
    updateSequenceNumber = 10
  )

  "GetAttachmentDetailsResponse JSON format" should {

    "round-trip a fully populated response" in {
      Json.toJson(fullResponse).as[GetAttachmentDetailsResponse] shouldBe fullResponse
    }

    "omit absent optional fields from the JSON" in {
      val minimal = GetAttachmentDetailsResponse(
        attachmentNumber     = 11,
        fileName             = None,
        fileType             = None,
        fileDescription      = None,
        fileSize             = None,
        updateSequenceNumber = 10
      )

      Json.toJson(minimal) shouldBe Json.obj(
        "attachmentNumber" -> 11,
        "updateSequenceNumber" -> 10
      )
      Json.toJson(minimal).as[GetAttachmentDetailsResponse] shouldBe minimal
    }
  }
}
