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

class ApplicationResponseSpec extends AnyWordSpec with Matchers {

  "ApplicationResponse JSON format" should {

    "serialize to JSON correctly" in {
      val response = ApplicationResponse(
        applicationId     = 1,
        applicationNumber = "GB123456",
        updateSeqNumber   = 1
      )

      val json = Json.toJson(response)

      json shouldBe Json.obj(
        "applicationId"     -> 1,
        "applicationNumber" -> "GB123456",
        "updateSeqNumber"   -> 1
      )
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "applicationId"     -> 1,
        "applicationNumber" -> "GB123456",
        "updateSeqNumber"   -> 1
      )

      val result = json.as[ApplicationResponse]

      result shouldBe ApplicationResponse(
        applicationId     = 1,
        applicationNumber = "GB123456",
        updateSeqNumber   = 1
      )
    }
  }
}
