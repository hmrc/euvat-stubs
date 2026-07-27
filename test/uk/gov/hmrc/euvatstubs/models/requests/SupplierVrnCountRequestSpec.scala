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
import uk.gov.hmrc.euvatstubs.models.SupplierVrnCountRequest

class SupplierVrnCountRequestSpec extends AnyWordSpec with Matchers {

  "SupplierVrnCountRequest JSON format" should {

    "serialize to JSON correctly" in {
      val request = SupplierVrnCountRequest(
        applicationId = 133,
        itemNumber    = 4,
        vatNumber     = "500000881",
        invoiceNumber = "a444"
      )

      val json = Json.toJson(request)

      (json \ "applicationId").as[Long]   shouldBe 133L
      (json \ "itemNumber").as[Int]       shouldBe 4
      (json \ "vatNumber").as[String]     shouldBe "500000881"
      (json \ "invoiceNumber").as[String] shouldBe "a444"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "applicationId" -> 133,
        "itemNumber"    -> 4,
        "vatNumber"     -> "500000881",
        "invoiceNumber" -> "a444"
      )

      val result = json.as[SupplierVrnCountRequest]

      result.applicationId shouldBe 133L
      result.itemNumber    shouldBe 4
      result.vatNumber     shouldBe "500000881"
      result.invoiceNumber shouldBe "a444"
    }

    "support round-trip JSON conversion" in {
      val original = SupplierVrnCountRequest(
        applicationId = 133,
        itemNumber    = 4,
        vatNumber     = "500000881",
        invoiceNumber = "a444"
      )

      val json = Json.toJson(original)
      val parsed = json.as[SupplierVrnCountRequest]

      parsed shouldBe original
    }
  }
}
