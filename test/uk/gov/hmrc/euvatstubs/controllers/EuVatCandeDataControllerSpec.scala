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
import play.api.libs.json.*
import play.api.test.*
import play.api.test.Helpers.*

class EuVatCandeDataControllerSpec extends PlaySpec with GuiceOneAppPerSuite {

  implicit val system: ActorSystem = ActorSystem("test")
  implicit val mat: Materializer = Materializer(system)

  val controller = new EuVatCandeDataController(stubControllerComponents())

  "EuVatCandeDataController.addApplication" should {
    val fakeRequest = FakeRequest("POST", s"/create-application")

    "save the refund application for vrn ending with 111" in {
      val result = controller.addApplication("111")(fakeRequest)

      status(result) mustBe OK

      val json: JsValue = contentAsJson(result)
      (json \ "applicationId").as[Int] mustBe 111
      (json \ "applicationNumber").as[String] mustBe "GB123111"
      (json \ "updateSeqNumber").as[Int] mustBe 1
    }

    "save the refund application for vrn ending with 999" in {
      val result = controller.addApplication("999")(fakeRequest)

      status(result) mustBe OK

      val json: JsValue = contentAsJson(result)
      (json \ "applicationId").as[Int] mustBe 999
      (json \ "applicationNumber").as[String] mustBe "GB123999"
      (json \ "updateSeqNumber").as[Int] mustBe 1
    }

    "save the refund application for vrn ending with 666" in {
      val result = controller.addApplication("666")(fakeRequest)

      status(result) mustBe OK

      val json: JsValue = contentAsJson(result)
      (json \ "applicationId").as[Int] mustBe 666
      (json \ "applicationNumber").as[String] mustBe "GB123666"
      (json \ "updateSeqNumber").as[Int] mustBe 1
    }

    "save the refund application for any other vrn" in {
      val result = controller.addApplication("333")(fakeRequest)

      status(result) mustBe OK

      val json: JsValue = contentAsJson(result)
      (json \ "applicationId").as[Int] mustBe 100
      (json \ "applicationNumber").as[String] mustBe "GB123100"
      (json \ "updateSeqNumber").as[Int] mustBe 3
    }

  }

}
