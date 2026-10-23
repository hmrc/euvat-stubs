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

class SadReferenceCountRequestSpec extends AnyWordSpec with Matchers {

  "SadReferenceCountRequest JSON format" should {

    "serialize to JSON correctly" in {
      val request = SadReferenceCountRequest(
        applicationId      = 133,
        itemNumber         = 4,
        sadReferenceNumber = "500000881"
      )

      val json = Json.toJson(request)

      (json \ "applicationId").as[Long]        shouldBe 133L
      (json \ "itemNumber").as[Int]            shouldBe 4
      (json \ "sadReferenceNumber").as[String] shouldBe "500000881"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "applicationId"      -> 133,
        "itemNumber"         -> 4,
        "sadReferenceNumber" -> "500000881"
      )

      val result = json.as[SadReferenceCountRequest]

      result.applicationId      shouldBe 133L
      result.itemNumber         shouldBe 4
      result.sadReferenceNumber shouldBe "500000881"
    }

    "support round-trip JSON conversion" in {
      val original = SadReferenceCountRequest(
        applicationId      = 133,
        itemNumber         = 4,
        sadReferenceNumber = "500000881"
      )

      val json = Json.toJson(original)
      val parsed = json.as[SadReferenceCountRequest]

      parsed shouldBe original
    }
  }
}
