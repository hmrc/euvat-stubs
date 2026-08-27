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

import java.time.LocalDateTime

class UpdatePurchaseDetailsRequestSpec extends AnyWordSpec with Matchers {

  private val request = UpdatePurchaseDetailsRequest(
    applicationId = 404,
    itemNumber = 4,
    goodsDescriptionCategory = "10",
    goodsDescriptionSubCategory = Some("10.4.1"),
    goodsDescriptionText = Some("office stationery and consumables"),
    simplifiedInvoiceIndicator = Some("N"),
    supplierName = Some("Finnish International"),
    supplierAddress1 = Some("356 High Street"),
    supplierAddress2 = Some("Rochdale"),
    supplierAddress3 = Some("England"),
    supplierVatRegNumber = Some("500000881"),
    supplierTaxIdentifier = Some("") ,
    invoiceDate = Some(LocalDateTime.of(2026,5,14,0,0)),
    invoiceNumber = Some("a444"),
    currencyCode = Some("EUR"),
    taxableAmount = Some(BigDecimal(1000)),
    vatAmount = Some(BigDecimal(99)),
    deductibleVatAmount = Some(BigDecimal(40)),
    updateSequenceNumber = 2
  )

  "UpdatePurchaseDetailsRequest JSON format" should {

    "serialize and deserialize correctly" in {
      Json.toJson(request).as[UpdatePurchaseDetailsRequest] shouldBe request
    }

    "deserialize when optional fields are absent" in {
      val result = Json
        .obj(
          "applicationId"            -> 123456,
          "itemNumber"               -> 0,
          "goodsDescriptionCategory" -> "1",
          "updateSequenceNumber"     -> 1
        )
        .as[UpdatePurchaseDetailsRequest]

      result.applicationId shouldBe 123456L
      result.supplierName  shouldBe None
      result.invoiceDate   shouldBe None
    }
  }
}
