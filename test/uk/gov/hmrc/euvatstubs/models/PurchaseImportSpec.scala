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

package uk.gov.hmrc.euvatstubs.models

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class PurchaseImportSpec extends AnyWordSpec with Matchers {

  "PurchaseImport JSON format" should {
    "serialize and deserialize correctly" in {
      val model = PurchaseImport(
        itemNumber                  = 1,
        goodsDescriptionCategory    = "1",
        goodsDescriptionSubCategory = Some("1.2"),
        currencyCode                = "DE",
        taxableAmount               = BigDecimal(333),
        vatAmount                   = BigDecimal(222),
        deductibleVatAmount         = BigDecimal(111.11),
        itemType                    = "P"
      )

      Json.toJson(model).as[PurchaseImport] shouldBe model
    }
  }
}
