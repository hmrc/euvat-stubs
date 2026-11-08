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
    goodsDescriptionCode       = "7",
    goodsDescriptionSubCode    = Some("7.1"),
    goodsDescriptionText       = Some(""),
    simplifiedInvoiceIndicator = Some("N"),
    supplierName               = Some("Test Supplier Name"),
    supplierAddressLine1       = Some("Test address one"),
    supplierAddressLine2       = Some("Test address two"),
    supplierAddressLine3       = None,
    supplierVatNumber          = Some("EE0000000111"),
    supplierTaxIdentifier      = None,
    invoiceDate                = Some(LocalDateTime.of(2025, 12, 8, 0, 0)),
    invoiceNumber              = Some("1234567890"),
    currencyCode               = Some("EUR"),
    taxableAmount              = Some(BigDecimal("1000.01")),
    vatAmount                  = Some(BigDecimal("200.01")),
    deductibleVatAmount        = Some(BigDecimal("100.01")),
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
