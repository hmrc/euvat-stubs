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

import java.time.LocalDateTime

class GetPurchaseDetailsResponseSpec extends AnyWordSpec with Matchers {

  private val fullResponse = GetPurchaseDetailsResponse(
    goodsDescriptionCode       = "1",
    goodsDescriptionSubCode    = Some("1.1"),
    goodsDescriptionText       = Some("Fuel"),
    simplifiedInvoiceIndicator = Some("N"),
    supplierName               = Some("Supplier Ltd"),
    supplierAddressLine1       = Some("1 High Street"),
    supplierAddressLine2       = Some("Riga"),
    supplierAddressLine3       = None,
    supplierVatNumber          = Some("LV40003567907"),
    supplierTaxIdentifier      = None,
    invoiceDate                = Some(LocalDateTime.of(2025, 3, 15, 0, 0)),
    invoiceNumber              = Some("INV-001"),
    currencyCode               = Some("EUR"),
    taxableAmount              = Some(BigDecimal("100.50")),
    vatAmount                  = Some(BigDecimal("21.10")),
    deductibleVatAmount        = Some(BigDecimal("21.10")),
    updateSequenceNumber       = 1
  )

  "GetPurchaseDetailsResponse JSON format" should {

    "round-trip a fully populated response" in {
      Json.toJson(fullResponse).as[GetPurchaseDetailsResponse] shouldBe fullResponse
    }

    "omit absent optional fields from the JSON" in {
      val minimal = fullResponse.copy(
        goodsDescriptionSubCode    = None,
        goodsDescriptionText       = None,
        simplifiedInvoiceIndicator = None,
        supplierName               = None,
        supplierAddressLine1       = None,
        supplierAddressLine2       = None,
        supplierVatNumber          = None,
        invoiceDate                = None,
        invoiceNumber              = None,
        currencyCode               = None,
        taxableAmount              = None,
        vatAmount                  = None,
        deductibleVatAmount        = None
      )

      Json.toJson(minimal) shouldBe Json.obj(
        "goodsDescriptionCode" -> "1",
        "updateSequenceNumber" -> 1
      )
      Json.toJson(minimal).as[GetPurchaseDetailsResponse] shouldBe minimal
    }
  }
}
