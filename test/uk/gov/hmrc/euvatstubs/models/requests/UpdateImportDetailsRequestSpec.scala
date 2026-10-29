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
import play.api.libs.json.{JsObject, Json}

import java.time.LocalDateTime

class UpdateImportDetailsRequestSpec extends AnyWordSpec with Matchers {

  private val fullRequest = UpdateImportDetailsRequest(
    applicationId               = 404,
    itemNumber                  = 4,
    goodsDescriptionCategory    = "10",
    goodsDescriptionSubCategory = Some("10.4.1"),
    goodsDescriptionText        = Some("office stationery and consumables"),
    hasSadReferenceNumber       = Some("true"),
    sadReferenceNumber          = Some("SAD123456789"),
    referenceInformation        = Some("Import declaration reference"),
    supplierName                = Some("Finnish International"),
    supplierAddressLine1        = Some("356 High Street"),
    supplierAddressLine2        = Some("Rochdale"),
    supplierAddressLine3        = Some("England"),
    supplierCountryCode         = Some("GB"),
    issuingDate                 = Some(LocalDateTime.of(2026, 5, 14, 0, 0)),
    currencyCode                = Some("EUR"),
    taxableAmount               = Some(BigDecimal(1000)),
    vatAmount                   = Some(BigDecimal(99)),
    deductibleVatAmount         = Some(BigDecimal(40)),
    updateSequenceNumber        = 1
  )

  private val fullJson: JsObject = Json.obj(
    "applicationId"               -> 404,
    "itemNumber"                  -> 4,
    "goodsDescriptionCategory"    -> "10",
    "goodsDescriptionSubCategory" -> "10.4.1",
    "goodsDescriptionText"        -> "office stationery and consumables",
    "hasSadReferenceNumber"       -> "true",
    "sadReferenceNumber"          -> "SAD123456789",
    "referenceInformation"        -> "Import declaration reference",
    "supplierName"                -> "Finnish International",
    "supplierAddressLine1"        -> "356 High Street",
    "supplierAddressLine2"        -> "Rochdale",
    "supplierAddressLine3"        -> "England",
    "supplierCountryCode"         -> "GB",
    "issuingDate"                 -> "2026-05-14T00:00:00",
    "currencyCode"                -> "EUR",
    "taxableAmount"               -> 1000,
    "vatAmount"                   -> 99,
    "deductibleVatAmount"         -> 40,
    "updateSequenceNumber"        -> 1
  )

  "UpdateImportDetailsRequest JSON format" should {

    "serialize to JSON correctly" in {
      Json.toJson(fullRequest) shouldBe fullJson
    }

    "deserialize from JSON correctly" in {
      fullJson.as[UpdateImportDetailsRequest] shouldBe fullRequest
    }

    "deserialize with all optional fields absent" in {
      val json = Json.obj(
        "applicationId"            -> 404,
        "itemNumber"               -> 4,
        "goodsDescriptionCategory" -> "10",
        "updateSequenceNumber"     -> 1
      )

      json.as[UpdateImportDetailsRequest] shouldBe UpdateImportDetailsRequest(
        applicationId            = 404,
        itemNumber               = 4,
        goodsDescriptionCategory = "10",
        updateSequenceNumber     = 1
      )
    }

    "round trip through JSON" in {
      Json.toJson(fullRequest).as[UpdateImportDetailsRequest] shouldBe fullRequest
    }

    Seq("applicationId", "itemNumber", "goodsDescriptionCategory", "updateSequenceNumber").foreach { field =>
      s"fail to deserialize when mandatory field $field is missing" in {
        (fullJson - field).validate[UpdateImportDetailsRequest].isError shouldBe true
      }
    }

    "fail to deserialize when applicationId is not a number" in {
      (fullJson + ("applicationId" -> Json.toJson("abc"))).validate[UpdateImportDetailsRequest].isError shouldBe true
    }
  }
}
