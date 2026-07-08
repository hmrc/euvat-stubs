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
    "save the refund application" in {
      val fakeRequest = FakeRequest("POST", s"/create-application")

      val result = controller.addApplication()(fakeRequest)

      status(result) `mustBe` OK

      val json: JsValue = contentAsJson(result)
      (json \ "applicationId").as[Int] mustBe 101
      (json \ "applicationNumber").as[String] mustBe "GB123101"
      (json \ "updateSeqNumber").as[Int] mustBe 1
    }
  }

}
