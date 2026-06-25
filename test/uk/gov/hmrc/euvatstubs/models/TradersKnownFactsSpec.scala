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

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json.*
import java.time.LocalDateTime

class TradersKnownFactsSpec extends AnyWordSpec with Matchers {

  "TradersKnownFacts JSON format" should {

    "serialise to JSON correctly" in {
      val model = TradersKnownFacts(
        vatRegNumber           = 123456,
        traderName             = "Test Trader",
        addressLine1           = "Line 1",
        addressLine2           = "Line 2",
        addressLine3           = "Line 3",
        addressLine4           = "Line 4",
        addressLine5           = "Line 5",
        postCode               = "AB12 3CD",
        tradeClass             = "7020",
        dateOfRegistration     = Some(LocalDateTime.of(2025, 5, 20, 10, 38)),
        dateOfDeregistration   = None,
        missingTraderIndicator = "N"
      )

      val json = Json.toJson(model)

      (json \ "vatRegNumber").as[Int] mustBe 123456
      (json \ "traderName").as[String] mustBe "Test Trader"
      (json \ "addressLine1").as[String] mustBe "Line 1"
      (json \ "tradeClass").as[String] mustBe "7020"
      (json \ "dateOfRegistration").as[String] mustBe "2025-05-20T10:38:00"
      (json \ "dateOfDeregistration").asOpt[String] mustBe None
      (json \ "missingTraderIndicator").as[String] mustBe "N"
    }

    "deserialise from JSON correctly" in {
      val json = Json.obj(
        "vatRegNumber"           -> 123456,
        "traderName"             -> "Test Trader",
        "addressLine1"           -> "Line 1",
        "addressLine2"           -> "Line 2",
        "addressLine3"           -> "Line 3",
        "addressLine4"           -> "Line 4",
        "addressLine5"           -> "Line 5",
        "postCode"               -> "AB12 3CD",
        "tradeClass"             -> "7020",
        "dateOfRegistration"     -> "2025-05-20T10:38:00",
        "dateOfDeregistration"   -> JsNull,
        "missingTraderIndicator" -> "N"
      )

      val result = json.as[TradersKnownFacts]

      result.vatRegNumber mustBe 123456
      result.traderName mustBe "Test Trader"
      result.addressLine1 mustBe "Line 1"
      result.tradeClass mustBe "7020"
      result.dateOfRegistration mustBe Some(LocalDateTime.of(2025, 5, 20, 10, 38))
      result.dateOfDeregistration mustBe None
      result.missingTraderIndicator mustBe "N"
    }
  }
}
