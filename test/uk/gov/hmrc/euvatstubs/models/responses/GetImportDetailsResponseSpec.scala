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

class GetImportDetailsResponseSpec extends AnyWordSpec with Matchers {

  private val fullResponse = GetImportDetailsResponse(
    applicationId               = 404,
    itemNumber                  = 4,
    issuingDate                 = Some(LocalDateTime.of(2026, 5, 14, 0, 0)),
    supplierName                = Some("Finnish International"),
    supplierAddressLine1        = Some("356 High Street"),
    supplierAddressLine2        = Some("Rochdale"),
    supplierAddressLine3        = Some("England"),
    supplierCountryCode         = Some("GB"),
    goodsDescriptionCategory    = "10",
    goodsDescriptionSubcategory = Some("10.4.1"),
    goodsDescriptionText        = Some(""),
    hasSadReferenceNumber       = Some("Y"),
    sadReferenceNumber          = Some("SAD123456789"),
    referenceInformation        = Some("Import declaration reference"),
    currencyCode                = Some("EUR"),
    taxableAmount               = Some(BigDecimal("1000")),
    vatAmount                   = Some(BigDecimal("99")),
    deductibleVatAmount         = Some(BigDecimal("40")),
    updateSequenceNumber        = 1,
    periodStartDate             = LocalDateTime.of(2026, 1, 1, 0, 0)
  )

  "GetImportDetailsResponse JSON format" should {

    "round-trip a fully populated response" in {
      val json = Json.toJson(fullResponse)

      (json \ "supplierAddressLine2").as[String] shouldBe "Rochdale"
      (json \ "supplierAddressLine3").as[String] shouldBe "England"
      json.as[GetImportDetailsResponse]          shouldBe fullResponse
    }

    "omit absent optional fields from the JSON" in {
      val minimal = fullResponse.copy(
        issuingDate                 = None,
        supplierName                = None,
        supplierAddressLine1        = None,
        supplierAddressLine2        = None,
        supplierAddressLine3        = None,
        supplierCountryCode         = None,
        goodsDescriptionSubcategory = None,
        goodsDescriptionText        = None,
        hasSadReferenceNumber       = None,
        sadReferenceNumber          = None,
        referenceInformation        = None,
        currencyCode                = None,
        taxableAmount               = None,
        vatAmount                   = None,
        deductibleVatAmount         = None
      )

      val json = Json.toJson(minimal)

      json.as[GetImportDetailsResponse] shouldBe minimal
    }
  }
}
