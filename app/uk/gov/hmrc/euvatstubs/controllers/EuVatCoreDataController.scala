/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.euvatstubs.controllers

import play.api.Logging
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.euvatstubs.models.TradersKnownFacts
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}

@Singleton
class EuVatCoreDataController @Inject()(cc: ControllerComponents) extends BackendController(cc) with Logging:

  private def knownFactsResponse(vrn: String, tradeClass: String): TradersKnownFacts = TradersKnownFacts(
    vatRegNumber = vrn.toInt,
    traderName = "TestData",
    addressLine1 = "Line 1",
    addressLine2 = "Line 2",
    addressLine3 = "Line 3",
    addressLine4 = "Line 4",
    addressLine5 = "Line 5",
    postCode = "NE3 9TG",
    tradeClass = tradeClass,
    dateOfRegistration = LocalDateTime.of(2025, 1, 11, 10, 38),
    dateOfDeregistration = LocalDateTime.of(2026, 1, 11, 10, 38),
    missingTraderIndicator = "N",
    singleMarketIndicator = 1
  )

  def getTraderByVrn(vrn: String): Action[AnyContent] = Action {
    implicit request =>
      logger.info(s"Stub: returning known facts for VRN: $vrn")

      val response = if (vrn.endsWith("111")) {
        knownFactsResponse(vrn, "1111")
      } else if (vrn.endsWith("999")) {
        knownFactsResponse(vrn, "9999")
      } else {
        knownFactsResponse(vrn, "7020")
      }

      Ok(Json.toJson(response))

  }
