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

package uk.gov.hmrc.euvatstubs.controllers

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalatestplus.play.*
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.*
import play.api.test.Helpers.*
import play.api.libs.json.*
import uk.gov.hmrc.euvatstubs.models.TradersKnownFacts

import java.time.LocalDateTime

class EuVatCoreDataControllerSpec extends PlaySpec with GuiceOneAppPerSuite {

  implicit val system: ActorSystem = ActorSystem("test")
  implicit val mat: Materializer = Materializer(system)

  val controller = new EuVatCoreDataController(stubControllerComponents())

  "EuVatCoreDataController.getTraderByVrn" should {

    "return tradeClass 1111 when VRN ends with 111" in {
      val vrn = "123111"

      val fakeRequest = FakeRequest("GET", s"/traders/getKnownFacts/$vrn")
        .withBody(Json.toJson(dummyTrader(vrn)))

      val result = controller.getTraderByVrn(vrn)(fakeRequest)

      status(result) mustBe OK

      val json = contentAsJson(result)
      (json \ "tradeClass").as[String] mustBe "1111"
    }

    "return tradeClass 9999 when VRN ends with 999" in {
      val vrn = "123999"

      val fakeRequest = FakeRequest("GET", s"/traders/getKnownFacts/$vrn")
        .withBody(Json.toJson(dummyTrader(vrn)))

      val result = controller.getTraderByVrn(vrn)(fakeRequest)

      status(result) mustBe OK

      val json = contentAsJson(result)
      (json \ "tradeClass").as[String] mustBe "9999"
    }

    "return tradeClass 7020 for all other VRNs" in {
      val vrn = "123456"

      val fakeRequest = FakeRequest("GET", s"/traders/getKnownFacts/$vrn")
        .withBody(Json.toJson(dummyTrader(vrn)))

      val result = controller.getTraderByVrn(vrn)(fakeRequest)

      status(result) mustBe OK

      val json = contentAsJson(result)
      (json \ "tradeClass").as[String] mustBe "7020"
    }
  }

  "EuVatCoreDataController.getLatestApplications" should {
    "return a valid Application" in {
      val fakeRequest = FakeRequest("POST", s"/get-latest-application")

      val result = controller.getLatestApplications()(fakeRequest)

      status(result) `mustBe` OK

      val json: JsValue = contentAsJson(result)
      val total = (json \ "totalApplication").toOption.flatMap(_.asOpt[Int])
      total mustBe Some(1)
    }
  }

  private def dummyTrader(vrn: String): TradersKnownFacts =
    TradersKnownFacts(
      vatRegNumber           = vrn.toInt,
      traderName             = "Test",
      addressLine1           = "A",
      addressLine2           = "B",
      addressLine3           = "C",
      addressLine4           = "D",
      addressLine5           = "E",
      postCode               = "NE3 9TG",
      tradeClass             = "dummy",
      dateOfRegistration     = Some(LocalDateTime.now()),
      dateOfDeregistration   = Some(LocalDateTime.now()),
      missingTraderIndicator = "N",
      singleMarketIndicator  = 1
    )
}
