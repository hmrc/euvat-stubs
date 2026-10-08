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

class UpdateApplicationDetailsRequestSpec extends AnyWordSpec with Matchers {

  private val fullRequest = UpdateApplicationDetailsRequest(
    applicationId              = 133,
    applicationLanguage        = "en",
    refundingCountry           = "LV",
    periodStartDate            = LocalDateTime.of(2011, 6, 1, 0, 0, 0),
    periodEndDate              = LocalDateTime.of(2011, 10, 31, 23, 59, 59),
    applicantEmailAddress      = "test@hotmail.com",
    applicantPhoneNumber       = Some("01952233299"),
    representativeCountry      = Some("AO"),
    representativeEmailAddress = Some("johnbloggs@hotmail.com"),
    representativePhoneNumber  = Some("01952233248"),
    bankAccountOwnerName       = Some("test account 1"),
    bankAccountOwnerType       = Some("applicant"),
    ibanCode                   = Some("RoCe/FJvoYSi6rSsMQ5D8UU9QirCB9MZXjC6wuYDyhc="),
    bicCode                    = Some("FOJFkUjXAjPmZTcK4WRCyw=="),
    bankAccountCurrencyCode    = Some("EUR"),
    businessActivityCode2      = Some("4477"),
    businessActivityCode3      = Some("2233"),
    cipherText                 = Some("badd2539694a0b3fd547b7cfcb77eefe"),
    encryptionStatus           = Some("M"),
    updateSequenceNumber       = 30
  )

  private val fullJson: JsObject = Json.obj(
    "applicationId"              -> 133,
    "applicationLanguage"        -> "en",
    "refundingCountry"           -> "LV",
    "periodStartDate"            -> "2011-06-01T00:00:00",
    "periodEndDate"              -> "2011-10-31T23:59:59",
    "applicantEmailAddress"      -> "test@hotmail.com",
    "applicantPhoneNumber"       -> "01952233299",
    "representativeCountry"      -> "AO",
    "representativeEmailAddress" -> "johnbloggs@hotmail.com",
    "representativePhoneNumber"  -> "01952233248",
    "bankAccountOwnerName"       -> "test account 1",
    "bankAccountOwnerType"       -> "applicant",
    "ibanCode"                   -> "RoCe/FJvoYSi6rSsMQ5D8UU9QirCB9MZXjC6wuYDyhc=",
    "bicCode"                    -> "FOJFkUjXAjPmZTcK4WRCyw==",
    "bankAccountCurrencyCode"    -> "EUR",
    "businessActivityCode2"      -> "4477",
    "businessActivityCode3"      -> "2233",
    "cipherText"                 -> "badd2539694a0b3fd547b7cfcb77eefe",
    "encryptionStatus"           -> "M",
    "updateSequenceNumber"       -> 30
  )

  private val mandatoryOnlyJson: JsObject = Json.obj(
    "applicationId"         -> 133,
    "applicationLanguage"   -> "en",
    "refundingCountry"      -> "LV",
    "periodStartDate"       -> "2011-06-01T00:00:00",
    "periodEndDate"         -> "2011-10-31T23:59:59",
    "applicantEmailAddress" -> "test@hotmail.com",
    "updateSequenceNumber"  -> 30
  )

  "UpdateApplicationDetailsRequest JSON format" should {

    "serialize to JSON correctly" in {
      Json.toJson(fullRequest) shouldBe fullJson
    }

    "deserialize from JSON correctly" in {
      fullJson.as[UpdateApplicationDetailsRequest] shouldBe fullRequest
    }

    "deserialize when only mandatory fields are present" in {
      mandatoryOnlyJson.as[UpdateApplicationDetailsRequest] shouldBe fullRequest.copy(
        applicantPhoneNumber       = None,
        representativeCountry      = None,
        representativeEmailAddress = None,
        representativePhoneNumber  = None,
        bankAccountOwnerName       = None,
        bankAccountOwnerType       = None,
        ibanCode                   = None,
        bicCode                    = None,
        bankAccountCurrencyCode    = None,
        businessActivityCode2      = None,
        businessActivityCode3      = None,
        cipherText                 = None,
        encryptionStatus           = None
      )
    }

    Seq(
      "applicationId",
      "applicationLanguage",
      "refundingCountry",
      "periodStartDate",
      "periodEndDate",
      "applicantEmailAddress",
      "updateSequenceNumber"
    ).foreach { field =>
      s"fail to deserialize when $field is missing" in {
        (mandatoryOnlyJson - field).validate[UpdateApplicationDetailsRequest].isError shouldBe true
      }
    }
  }
}
