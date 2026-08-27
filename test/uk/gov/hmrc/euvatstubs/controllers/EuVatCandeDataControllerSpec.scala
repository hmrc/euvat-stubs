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
import scala.concurrent.Future

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

    "return base apps when date parsing fails" in {
      val fakeRequest = FakeRequest("POST", "/get-latest-application")
        .withJsonBody(
          Json.obj(
            "applicantVatRegNumber" -> "500000881",
            "refundingCountry"      -> "LV",
            "startDate"             -> "not-a-date",
            "endDate"               -> "also-not-a-date"
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getLatestApplications()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "totalApplication").as[Int] mustBe 1
    }

    "simulate SIM-5XX and return 500" in {
      val fakeRequest = FakeRequest("POST", "/get-latest-application")
        .withJsonBody(Json.obj("applicantVatRegNumber" -> "111111115"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getLatestApplications()(fakeRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "simulate SIM-4XX and return 400" in {
      val fakeRequest = FakeRequest("POST", "/get-latest-application")
        .withJsonBody(Json.obj("applicantVatRegNumber" -> "444444444"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getLatestApplications()(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }

    "simulate SIM-EMPTY and return empty list" in {
      val fakeRequest = FakeRequest("POST", "/get-latest-application")
        .withJsonBody(Json.obj("applicantVatRegNumber" -> "333333333"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getLatestApplications()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "totalApplication").as[Int] mustBe 0
    }

    "simulate SIM-STATE and return empty (test stub returns 1)" in {
      val fakeRequest = FakeRequest("POST", "/get-latest-application")
        .withJsonBody(Json.obj("applicantVatRegNumber" -> "555555555"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getLatestApplications()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "totalApplication").as[Int] mustBe 0
    }

    "simulate SIM-STATE and return application when counter triggers" in {
      val repo = new uk.gov.hmrc.euvatstubs.repositories.VrnStateRepository() {
        override def incrementAndGet(vrn: String): Future[Port] = scala.concurrent.Future.successful(3)
      }
      val ctrl = new EuVatCandeDataController(stubControllerComponents(), repo)

      val fakeRequest = FakeRequest("POST", "/get-latest-application")
        .withJsonBody(Json.obj("applicantVatRegNumber" -> "555555555"))
        .withHeaders("Content-Type" -> "application/json")

      val result = ctrl.getLatestApplications()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "totalApplication").as[Int] mustBe 1
      val apps = (json \ "applications").as[JsArray].value
      (apps.head \ "applicationNumber").as[String] must include("GB-DUP-STATE-")
    }

    "simulate SIM-DUP and return duplicate application" in {
      val fakeRequest = FakeRequest("POST", "/get-latest-application")
        .withJsonBody(Json.obj("applicantVatRegNumber" -> "111111111"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getLatestApplications()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      println(s"SIM-DUP response: ${contentAsString(result)}")
      (json \ "totalApplication").as[Int] mustBe 1
      val apps = (json \ "applications").as[JsArray].value
      (apps.head \ "applicationNumber").as[String] mustBe "GB-DUP-0001"
    }

    "simulate SIM-OK and return OK application" in {
      val fakeRequest = FakeRequest("POST", "/get-latest-application")
        .withJsonBody(Json.obj("applicantVatRegNumber" -> "222222222"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getLatestApplications()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "totalApplication").as[Int] mustBe 0
    }
  }

  "EuVatCandeDataController.getSupplierTaxIdentifierCount" should {
    "return duplicateCount 1 for taxIdentifier ending 111" in {
      val fakeRequest = FakeRequest("POST", "/get-supplier-taxIdentifier-count")
        .withJsonBody(
          Json.obj(
            "applicationId" -> 1,
            "itemNumber"    -> 1,
            "taxIdentifier" -> "ABC111",
            "invoiceNumber" -> "INV-1"
          )
        )

      val result = controller.getSupplierTaxIdentifierCount()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "duplicateCount").as[Int] mustBe 1
    }

    "return duplicateCount 2 for taxIdentifier ending 999" in {
      val fakeRequest = FakeRequest("POST", "/get-supplier-taxIdentifier-count")
        .withJsonBody(
          Json.obj(
            "applicationId" -> 1,
            "itemNumber"    -> 1,
            "taxIdentifier" -> "XYZ999",
            "invoiceNumber" -> "INV-2"
          )
        )

      val result = controller.getSupplierTaxIdentifierCount()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "duplicateCount").as[Int] mustBe 2
    }

    "return duplicateCount 0 for other taxIdentifier" in {
      val fakeRequest = FakeRequest("POST", "/get-supplier-taxIdentifier-count")
        .withJsonBody(
          Json.obj(
            "applicationId" -> 1,
            "itemNumber"    -> 1,
            "taxIdentifier" -> "NO_MATCH",
            "invoiceNumber" -> "INV-3"
          )
        )

      val result = controller.getSupplierTaxIdentifierCount()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "duplicateCount").as[Int] mustBe 0
    }

    "return BadRequest when taxIdentifier is missing" in {
      val fakeRequest = FakeRequest("POST", "/get-supplier-taxIdentifier-count")
        .withJsonBody(
          Json.obj(
            "applicationId" -> 1,
            "itemNumber"    -> 1,
            "invoiceNumber" -> "INV-4"
          )
        )

      val result = controller.getSupplierTaxIdentifierCount()(fakeRequest)

      status(result) mustBe BAD_REQUEST
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

  "EuVatCandeDataController.getSupplierVrnCount" should {

    def requestWith(vatNumber: String, invoiceNumber: String = "DUP") =
      FakeRequest("POST", "/get-supplier-vrn-count")
        .withJsonBody(
          Json.obj(
            "applicationId" -> 133,
            "itemNumber"    -> 4,
            "vatNumber"     -> vatNumber,
            "invoiceNumber" -> invoiceNumber
          )
        )
        .withHeaders("Content-Type" -> "application/json")

    "return a duplicate count of 1 for a vat number ending with 111" in {
      val result = controller.getSupplierVrnCount()(requestWith("500000111"))

      status(result) mustBe OK
      (contentAsJson(result) \ "duplicateCount").as[Int] mustBe 1
    }

    "return a duplicate count of 2 for a vat number ending with 222" in {
      val result = controller.getSupplierVrnCount()(requestWith("500000222"))

      status(result) mustBe OK
      (contentAsJson(result) \ "duplicateCount").as[Int] mustBe 2
    }

    "return a duplicate count of 0 for any other vat number" in {
      val result = controller.getSupplierVrnCount()(requestWith("500000881"))

      status(result) mustBe OK
      (contentAsJson(result) \ "duplicateCount").as[Int] mustBe 0
    }

    "return 500 for a vat number ending with 500" in {
      val result = controller.getSupplierVrnCount()(requestWith("500000500"))
      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "return 400 when the request body is invalid" in {
      val fakeRequest = FakeRequest("POST", "/get-supplier-vrn-count")
        .withJsonBody(Json.obj("invalid" -> "body"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getSupplierVrnCount()(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }

    "return 0 when invoice is not DUP even if VRN suffix would duplicate" in {
      val result = controller.getSupplierVrnCount()(requestWith("500000111", "NOT_DUP"))

      status(result) mustBe OK
      (contentAsJson(result) \ "duplicateCount").as[Int] mustBe 0
    }
  }

  "EuVatCandeDataController.updatePurchaseDetails" should {
    "return updateSequenceNumber for valid UpdatePurchaseRequest" in {
      val fakeRequest = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 123456,
            "goodsDescriptionCategory" -> "1",
            "updateSequenceNumber"     -> 7
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.updatePurchaseDetails()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      json.as[JsObject].value("updateSequenceNumber").as[Int] mustBe 7
    }

    "accept full alternative request and return provided updateSequenceNumber" in {
      val fakeRequest = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"               -> 777L,
            "itemNumber"                  -> 5,
            "goodsDescriptionCategory"    -> "2",
            "goodsDescriptionSubCategory" -> "sub",
            "goodsDescriptionText"        -> "desc",
            "simplifiedInvoiceIndicator"  -> "Y",
            "supplierName"                -> "Acme Ltd",
            "supplierAddressLine1"        -> "1 Road",
            "supplierAddressLine2"        -> "Suite",
            "supplierAddressLine3"        -> "Area",
            "supplierVatNumber"           -> "500000111",
            "supplierTaxIdentifier"       -> "TAX111",
            "invoiceDate"                 -> "2025-03-10T12:34:56",
            "invoiceNumber"               -> "INV-99",
            "currencyCode"                -> "GBP",
            "taxableAmount"               -> 100.5,
            "vatAmount"                   -> 20.1,
            "deductibleVatAmount"         -> 20.1,
            "updateSequenceNumber"        -> 42
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.updatePurchaseDetails()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      json.as[JsObject].value("updateSequenceNumber").as[Int] mustBe 42
    }

    "succeed when goodsDescriptionText is absent" in {
      val fakeRequest = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"               -> 404,
            "itemNumber"                  -> 4,
            "goodsDescriptionCategory"    -> "10",
            "goodsDescriptionSubCategory" -> "10.4.1",
            "simplifiedInvoiceIndicator"  -> "N",
            "supplierName"                -> "Finnish International",
            "supplierAddressLine1"        -> "356 High Street",
            "invoiceNumber"               -> "a444",
            "currencyCode"                -> "EUR",
            "taxableAmount"               -> 1000,
            "vatAmount"                   -> 99,
            "deductibleVatAmount"         -> 40,
            "updateSequenceNumber"        -> 2
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.updatePurchaseDetails()(fakeRequest)

      status(result) mustBe OK
      (contentAsJson(result) \ "updateSequenceNumber").as[Int] mustBe 2
    }

    "succeed when goodsDescriptionSubCategory is absent" in {
      val fakeRequest = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"              -> 404,
            "itemNumber"                 -> 4,
            "goodsDescriptionCategory"   -> "10",
            "goodsDescriptionText"       -> "office stationery and consumables",
            "simplifiedInvoiceIndicator" -> "N",
            "supplierName"               -> "Finnish International",
            "supplierAddressLine1"       -> "356 High Street",
            "invoiceNumber"              -> "a444",
            "currencyCode"               -> "EUR",
            "taxableAmount"              -> 1000,
            "vatAmount"                  -> 99,
            "deductibleVatAmount"        -> 40,
            "updateSequenceNumber"       -> 3
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.updatePurchaseDetails()(fakeRequest)

      status(result) mustBe OK
      (contentAsJson(result) \ "updateSequenceNumber").as[Int] mustBe 3
    }

    "return BadRequest when no JSON body provided" in {
      val fakeRequest = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.updatePurchaseDetails()(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }

    "accept null invoiceDate" in {
      val fakeRequest = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 404,
            "goodsDescriptionCategory" -> "10",
            "invoiceDate"              -> JsNull,
            "updateSequenceNumber"     -> 5
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.updatePurchaseDetails()(fakeRequest)

      status(result) mustBe OK
      (contentAsJson(result) \ "updateSequenceNumber").as[Int] mustBe 5
    }

    "simulate INV-UP-500 and return 500" in {
      val fakeRequest = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(Json.obj("invoiceNumber" -> "INV-UP-500"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.updatePurchaseDetails()(fakeRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "accept and ignore an extra mode field" in {
      val fakeRequest = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 123456,
            "goodsDescriptionCategory" -> "1",
            "updateSequenceNumber"     -> 9,
            "mode"                     -> "FULL"
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.updatePurchaseDetails()(fakeRequest)

      status(result) mustBe OK
      (contentAsJson(result) \ "updateSequenceNumber").as[Int] mustBe 9
    }

    "return BadRequest when required fields are missing" in {
      val fakeRequest = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(Json.obj())
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.updatePurchaseDetails()(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }

    "simulate INV-UP-400 and return 400" in {
      val fakeRequest = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(Json.obj("invoiceNumber" -> "INV-UP-400"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.updatePurchaseDetails()(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }
  }

}
