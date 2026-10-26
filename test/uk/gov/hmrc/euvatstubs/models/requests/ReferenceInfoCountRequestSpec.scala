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

class ReferenceInfoCountRequestSpec extends AnyWordSpec with Matchers {

  "ReferenceInfoCountRequest JSON format" should {

    "serialize and deserialize a fully populated request" in {
      val request = ReferenceInfoCountRequest(
        applicationId = 123456L,
        itemNumber    = 2
      )

      Json.toJson(request).as[ReferenceInfoCountRequest] shouldBe request
    }

    "deserialize when optional fields are absent" in {
      val json = Json.obj(
        "applicationId" -> 123456,
        "itemNumber"    -> 2
      )

      val result = json.as[ReferenceInfoCountRequest]

      result.applicationId shouldBe 123456L
      result.itemNumber    shouldBe 2
    }
  }
}
