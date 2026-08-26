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
import uk.gov.hmrc.euvatstubs.models.SupplierVrnCountResponse

class SupplierVrnCountResponseSpec extends AnyWordSpec with Matchers {

  "SupplierVrnCountResponse JSON format" should {

    "serialize to JSON correctly" in {
      val response = SupplierVrnCountResponse(duplicateCount = 3)

      Json.toJson(response) shouldBe Json.obj("duplicateCount" -> 3)
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj("duplicateCount" -> 3)

      json.as[SupplierVrnCountResponse] shouldBe SupplierVrnCountResponse(duplicateCount = 3)
    }
  }
}
