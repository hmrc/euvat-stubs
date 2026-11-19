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

class DeleteAttachmentResponseSpec extends AnyWordSpec with Matchers {

  "DeleteAttachmentResponse JSON format" should {

    "serialize to JSON correctly" in {
      Json.toJson(DeleteAttachmentResponse(42)) shouldBe Json.obj("updateSequenceNumber" -> 42)
    }

    "deserialize from JSON correctly" in {
      Json.obj("updateSequenceNumber" -> 42).as[DeleteAttachmentResponse] shouldBe DeleteAttachmentResponse(42)
    }

    "fail to deserialize when updateSequenceNumber is missing" in {
      Json.obj().validate[DeleteAttachmentResponse].isError shouldBe true
    }
  }
}
