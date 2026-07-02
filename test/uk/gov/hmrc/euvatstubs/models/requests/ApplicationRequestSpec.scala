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

class ApplicationRequestSpec extends AnyWordSpec with Matchers {

  "ApplicationRequest JSON format" should {

    "serialize to JSON correctly" in {
      val request = ApplicationRequest(
        applicantVatRegNumber         = "GB123456",
        refundingCountryCode          = Some("FR"),
        periodStartDate               = Some(LocalDateTime.of(2024, 6, 1, 0, 0)),
        periodEndDate                 = Some(LocalDateTime.of(2024, 6, 30, 23, 59)),
        applicantEmailAddress         = Some("test@example.com"),
        applicantTelephoneNumber      = Some("12345"),
        applicationLanguage           = Some("EN"),
        businessActivityCode1         = Some("A1"),
        businessActivityCode2         = None,
        businessActivityCode3         = None,
        representativeId              = Some("REP123"),
        representativeCountryCode     = Some("GB"),
        representativeEmailAddress    = Some("rep@example.com"),
        representativeIdType          = Some("NINO"),
        representativeTelephoneNumber = Some("555-123"),
        bankAccountOwnerName          = Some("John Doe"),
        bankAccountOwnerType          = Some("Individual"),
        iBanCode                      = Some("GB29NWBK60161331926819"),
        bicCode                       = Some("NWBKGB2L"),
        bankAccountCurrencyCode       = Some("GBP")
      )

      val json = Json.toJson(request)

      (json \ "applicantVatRegNumber").as[String] shouldBe "GB123456"
      (json \ "refundingCountryCode").as[String]  shouldBe "FR"
      (json \ "periodStartDate").as[String]       shouldBe "2024-06-01T00:00:00"
      (json \ "periodEndDate").as[String]         shouldBe "2024-06-30T23:59:00"
      (json \ "applicantEmailAddress").as[String] shouldBe "test@example.com"
      (json \ "applicationLanguage").as[String]   shouldBe "EN"
      (json \ "businessActivityCode1").as[String] shouldBe "A1"
      (json \ "representativeId").as[String]      shouldBe "REP123"
      (json \ "bankAccountOwnerName").as[String]  shouldBe "John Doe"
      (json \ "iBanCode").as[String]              shouldBe "GB29NWBK60161331926819"
    }

    "deserialize from JSON correctly" in {
      val json = Json.obj(
        "applicantVatRegNumber"         -> "GB123456",
        "refundingCountryCode"          -> "FR",
        "periodStartDate"               -> "2024-06-01T00:00:00",
        "periodEndDate"                 -> "2024-06-30T23:59:00",
        "applicantEmailAddress"         -> "test@example.com",
        "applicantTelephoneNumber"      -> "12345",
        "applicationLanguage"           -> "EN",
        "businessActivityCode1"         -> "A1",
        "representativeId"              -> "REP123",
        "representativeCountryCode"     -> "GB",
        "representativeEmailAddress"    -> "rep@example.com",
        "representativeIdType"          -> "NINO",
        "representativeTelephoneNumber" -> "555-123",
        "bankAccountOwnerName"          -> "John Doe",
        "bankAccountOwnerType"          -> "Individual",
        "iBanCode"                      -> "GB29NWBK60161331926819",
        "bicCode"                       -> "NWBKGB2L",
        "bankAccountCurrencyCode"       -> "GBP"
      )

      val result = json.as[ApplicationRequest]

      result.applicantVatRegNumber shouldBe "GB123456"
      result.refundingCountryCode  shouldBe Some("FR")
      result.applicationLanguage   shouldBe Some("EN")
      result.businessActivityCode1 shouldBe Some("A1")
      result.representativeId      shouldBe Some("REP123")
      result.bankAccountOwnerName  shouldBe Some("John Doe")
      result.iBanCode              shouldBe Some("GB29NWBK60161331926819")
    }

    "support round‑trip JSON conversion" in {
      val original = ApplicationRequest(
        applicantVatRegNumber         = "GB999999",
        refundingCountryCode          = None,
        periodStartDate               = None,
        periodEndDate                 = None,
        applicantEmailAddress         = None,
        applicantTelephoneNumber      = None,
        applicationLanguage           = None,
        businessActivityCode1         = None,
        businessActivityCode2         = None,
        businessActivityCode3         = None,
        representativeId              = None,
        representativeCountryCode     = None,
        representativeEmailAddress    = None,
        representativeIdType          = None,
        representativeTelephoneNumber = None,
        bankAccountOwnerName          = None,
        bankAccountOwnerType          = None,
        iBanCode                      = None,
        bicCode                       = None,
        bankAccountCurrencyCode       = None
      )

      val json = Json.toJson(original)
      val parsed = json.as[ApplicationRequest]

      parsed shouldBe original
    }
  }
}
