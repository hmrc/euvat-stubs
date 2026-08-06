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
import scala.concurrent.ExecutionContext.Implicits.global

class EuVatCandeDataControllerSpec extends PlaySpec with GuiceOneAppPerSuite {

  implicit val system: ActorSystem = ActorSystem("test")
  implicit val mat: Materializer = Materializer(system)

  // Use the test stub repository (see test/.../repositories/VrnStateRepository.scala)
  implicit val ec: scala.concurrent.ExecutionContext = global

  val controller: EuVatCandeDataController = new EuVatCandeDataController(
    stubControllerComponents(),
    new uk.gov.hmrc.euvatstubs.repositories.VrnStateRepository()
  )

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

  "EuVatCandeDataController.getLatestApplications" should {
    "return a valid Application when country and dates overlap" in {
      val fakeRequest = FakeRequest("POST", "/get-latest-application")
        .withJsonBody(
          Json.obj(
            "applicantVatRegNumber" -> "500000881",
            "refundingCountry"      -> "LV",
            "startDate"             -> "2025-02-01T00:00:00",
            "endDate"               -> "2025-05-31T00:00:00"
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getLatestApplications()(fakeRequest)

      println(s"Response body: ${contentAsString(result)}")

      status(result) mustBe OK
      val json = contentAsJson(result)
      println(s"JSON: $json")
      (json \ "totalApplication").as[Int] mustBe 1
    }
    "return empty when country does not match" in {
      val fakeRequest = FakeRequest("POST", "/get-latest-application")
        .withJsonBody(
          Json.obj(
            "applicantVatRegNumber" -> "500000881",
            "refundingCountry"      -> "CZ",
            "startDate"             -> "2025-02-01T00:00:00",
            "endDate"               -> "2025-05-31T00:00:00"
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getLatestApplications()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "totalApplication").as[Int] mustBe 0
    }

    "return empty when dates do not overlap" in {
      val fakeRequest = FakeRequest("POST", "/get-latest-application")
        .withJsonBody(
          Json.obj(
            "applicantVatRegNumber" -> "500000881",
            "refundingCountry"      -> "LV",
            "startDate"             -> "2025-06-01T00:00:00",
            "endDate"               -> "2025-08-31T00:00:00"
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getLatestApplications()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "totalApplication").as[Int] mustBe 0
    }
  }

  "EuVatCandeDataController.addPurchase" should {

    val validRequest =
      FakeRequest("POST", "/add-purchase")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 123456,
            "goodsDescriptionCategory" -> "1",
            "updateSequenceNumber"     -> 1
          )
        )
        .withHeaders("Content-Type" -> "application/json")

    "return an item number and update sequence number for a valid request" in {
      val result = controller.addPurchase()(validRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "itemNumber").as[Int] mustBe 4
      (json \ "updateSequenceNumber").as[Int] mustBe 1
    }

    "return 400 when the request body is invalid" in {
      val fakeRequest = FakeRequest("POST", "/add-purchase")
        .withJsonBody(Json.obj("invalid" -> "body"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.addPurchase()(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }
  }

}
