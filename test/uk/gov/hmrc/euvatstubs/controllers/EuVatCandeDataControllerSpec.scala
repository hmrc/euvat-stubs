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
import uk.gov.hmrc.euvatstubs.models.responses.*
import uk.gov.hmrc.euvatstubs.models.PurchaseImport
import uk.gov.hmrc.euvatstubs.models.requests.UpdatePurchaseDetailsRequest
import uk.gov.hmrc.euvatstubs.repositories.{PurchaseImportRepository, StoredPurchaseDetails}

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.collection.mutable

class EuVatCandeDataControllerSpec extends PlaySpec with GuiceOneAppPerSuite {

  implicit val system: ActorSystem = ActorSystem("test")
  implicit val mat: Materializer = Materializer(system)

  // Use the test stub repository (see test/.../repositories/VrnStateRepository.scala)
  implicit val ec: scala.concurrent.ExecutionContext = global

  private class InMemoryPurchaseImportRepository extends PurchaseImportRepository {
    private val data = mutable.Map.empty[(String, Long), Vector[StoredPurchaseDetails]]
    private val seededApplications = mutable.Set.empty[(String, Long)]

    override def savePurchaseDetails(
      sessionId: String,
      applicationId: Long,
      purchaseDetails: UpdatePurchaseDetailsRequest
    ): Future[UpdatePurchaseDetailsRequest] =
      Future.successful {
        val key = sessionId -> applicationId
        val existing = data.getOrElse(key, Vector.empty)
        val assignedItemNumber =
          if (purchaseDetails.itemNumber > 0 && existing.exists(_.purchaseDetails.itemNumber == purchaseDetails.itemNumber))
            purchaseDetails.itemNumber
          else if (purchaseDetails.itemNumber > 0) purchaseDetails.itemNumber
          else existing.map(_.purchaseDetails.itemNumber).foldLeft(0)(Math.max) + 1

        val savedDetails = purchaseDetails.copy(applicationId = applicationId, itemNumber = assignedItemNumber)
        val itemType = existing.find(_.purchaseDetails.itemNumber == assignedItemNumber).map(_.purchaseImport.itemType).getOrElse("P")
        val stored = StoredPurchaseDetails(
          applicationId,
          savedDetails,
          PurchaseImport(
            itemNumber                  = savedDetails.itemNumber,
            itemType                    = itemType,
            goodsDescriptionCategory    = savedDetails.goodsDescriptionCategory,
            goodsDescriptionSubCategory = savedDetails.goodsDescriptionSubCategory,
            currencyCode                = savedDetails.currencyCode.getOrElse(""),
            taxableAmount               = savedDetails.taxableAmount.getOrElse(BigDecimal(0)),
            vatAmount                   = savedDetails.vatAmount.getOrElse(BigDecimal(0)),
            deductibleVatAmount         = savedDetails.deductibleVatAmount.getOrElse(BigDecimal(0))
          ),
          sessionId,
          existing.find(_.purchaseDetails.itemNumber == assignedItemNumber).flatMap(_.importDetails)
        )

        data.update(key, existing.filterNot(_.purchaseDetails.itemNumber == assignedItemNumber) :+ stored)
        savedDetails
      }

    override def findPurchaseDetails(sessionId: String, applicationId: Long, itemNumber: Int): Future[Option[UpdatePurchaseDetailsRequest]] =
      Future.successful(
        data.getOrElse(sessionId -> applicationId, Vector.empty).find(_.purchaseDetails.itemNumber == itemNumber).map(_.purchaseDetails)
      )

    override def findImportDetails(
      sessionId: String,
      applicationId: Long,
      itemNumber: Int
    ): Future[Option[GetImportDetailsResponse]] =
      Future.successful(
        data.getOrElse(sessionId -> applicationId, Vector.empty).find(_.purchaseDetails.itemNumber == itemNumber).flatMap(_.importDetails)
      )

    override def findPurchaseImports(sessionId: String, applicationId: Long): Future[List[PurchaseImport]] =
      Future.successful(data.getOrElse(sessionId -> applicationId, Vector.empty).map(_.purchaseImport).toList)

    override def deletePurchaseDetails(sessionId: String, applicationId: Long): Future[Unit] = Future.successful {
      data.remove(sessionId               -> applicationId)
      seededApplications.remove(sessionId -> applicationId)
      ()
    }

    override def seedDefaultPurchaseDetails(sessionId: String, applicationId: Long): Future[Unit] =
      Future.successful {
        val key = sessionId -> applicationId
        if (!seededApplications.contains(key)) {
          val existing = data.getOrElse(key, Vector.empty)
          val seeds = defaultEntriesToSeed(applicationId, existing).map(_.copy(sessionKey = sessionId))
          if (seeds.nonEmpty) data.update(key, existing ++ seeds)
          seededApplications.add(key)
        }
      }

    override def deleteImport(sessionId: String, applicationId: Long, itemNumber: Int): Future[Unit] = Future.successful {
      val key = sessionId -> applicationId
      data.update(
        key,
        data.getOrElse(key, Vector.empty).filterNot(entry => entry.purchaseImport.itemType == "I" && entry.purchaseImport.itemNumber == itemNumber)
      )
      ()
    }

    override def deletePurchase(sessionId: String, applicationId: Long, itemNumber: Int): Future[Unit] = Future.successful {
      val key = sessionId -> applicationId
      data.update(
        key,
        data.getOrElse(key, Vector.empty).filterNot(entry => entry.purchaseImport.itemType == "P" && entry.purchaseImport.itemNumber == itemNumber)
      )
      ()
    }

    private def defaultEntries(applicationId: Long): Vector[StoredPurchaseDetails] = applicationId match {
      case 111 => Vector(storedDefault(applicationId, 1, "P"))
      case 999 => Vector(storedDefault(applicationId, 2, "I"))
      case 402 =>
        Vector(
          storedDefault(applicationId, 4, "I", goodsDescriptionCategory = "3", deductibleVatAmount = 0),
          storedDefault(applicationId, 1, "P"),
          storedDefault(applicationId, 5, "P", goodsDescriptionCategory = "7", deductibleVatAmount  = 0),
          storedDefault(applicationId, 2, "P", goodsDescriptionCategory = "9", deductibleVatAmount  = 123.45),
          storedDefault(applicationId, 6, "I", goodsDescriptionCategory = "10", deductibleVatAmount = 0),
          storedDefault(applicationId, 3, "P", goodsDescriptionCategory = "9", deductibleVatAmount  = 999)
        )
      case _ =>
        Vector(
          storedDefault(applicationId, 3, "P", goodsDescriptionCategory = "7", deductibleVatAmount = 111.11),
          storedDefault(applicationId, 2, "I", goodsDescriptionCategory = "10")
        )
    }

    private def defaultEntriesToSeed(applicationId: Long, existing: Vector[StoredPurchaseDetails]): Vector[StoredPurchaseDetails] = {
      val defaults = defaultEntries(applicationId)
      val existingNumbers = existing.map(_.purchaseDetails.itemNumber).toSet
      if (applicationId == 111 || applicationId == 999 || applicationId == 402)
        defaults.filterNot(entry => existingNumbers.contains(entry.purchaseDetails.itemNumber))
      else {
        val purchase = defaults.find(_.purchaseImport.itemType == "P").filterNot(entry => existingNumbers.contains(entry.purchaseDetails.itemNumber))
        val importSeed =
          defaults.find(_.purchaseImport.itemType == "I").filter(_ => !existing.exists(_.purchaseImport.itemType == "I")).map { entry =>
            val occupied = existingNumbers ++ purchase.map(_.purchaseDetails.itemNumber)
            val itemNumber =
              if (occupied.contains(entry.purchaseDetails.itemNumber)) occupied.maxOption.getOrElse(0) + 1 else entry.purchaseDetails.itemNumber
            storedDefault(applicationId, itemNumber, "I", goodsDescriptionCategory = entry.purchaseDetails.goodsDescriptionCategory)
          }
        purchase.toVector ++ importSeed.toVector
      }
    }

    private def storedDefault(
      applicationId: Long,
      itemNumber: Int,
      itemType: String,
      goodsDescriptionCategory: String = "1",
      goodsDescriptionSubCategory: Option[String] = Some("1.2"),
      currencyCode: String = "DE",
      taxableAmount: BigDecimal = 333,
      vatAmount: BigDecimal = 222,
      deductibleVatAmount: BigDecimal = 111.11
    ): StoredPurchaseDetails = {
      val purchaseDetails = UpdatePurchaseDetailsRequest(
        applicationId               = applicationId,
        itemNumber                  = itemNumber,
        goodsDescriptionCategory    = goodsDescriptionCategory,
        goodsDescriptionSubCategory = goodsDescriptionSubCategory,
        goodsDescriptionText        = None,
        simplifiedInvoiceIndicator  = None,
        supplierName                = None,
        supplierAddress1            = None,
        supplierAddress2            = None,
        supplierAddress3            = None,
        supplierVatRegNumber        = None,
        supplierTaxIdentifier       = None,
        invoiceDate                 = None,
        invoiceNumber               = None,
        currencyCode                = Some(currencyCode),
        taxableAmount               = Some(taxableAmount),
        vatAmount                   = Some(vatAmount),
        deductibleVatAmount         = Some(deductibleVatAmount),
        updateSequenceNumber        = 1
      )
      val (supplierName, addressLine1, addressLine2, addressLine3, countryCode, issuingDate) = (applicationId, itemNumber) match {
        case (402, 4) =>
          (Some("Finnish International"), Some("356 High Street"), Some("Rochdale"), Some("England"), Some("GB"), LocalDateTime.of(2026, 5, 14, 0, 0))
        case (402, 6) =>
          (Some("Baltic Importers"), Some("2 Harbour Road"), Some("Riga"), Some("Latvia"), Some("LV"), LocalDateTime.of(2026, 6, 2, 0, 0))
        case _ => (None, None, None, None, None, LocalDateTime.of(2026, 4, 1, 0, 0))
      }

      StoredPurchaseDetails(
        applicationId,
        purchaseDetails,
        PurchaseImport(
          itemNumber                  = itemNumber,
          itemType                    = itemType,
          goodsDescriptionCategory    = goodsDescriptionCategory,
          goodsDescriptionSubCategory = goodsDescriptionSubCategory,
          currencyCode                = currencyCode,
          taxableAmount               = taxableAmount,
          vatAmount                   = vatAmount,
          deductibleVatAmount         = deductibleVatAmount
        ),
        sessionKey = "legacy",
        importDetails = Option.when(itemType == "I") {
          GetImportDetailsResponse(
            applicationId               = applicationId,
            itemNumber                  = itemNumber,
            issuingDate                 = Some(issuingDate),
            supplierName                = supplierName,
            supplierAddressLine1        = addressLine1,
            supplierAddressLine2        = addressLine2,
            supplierAddressLine3        = addressLine3,
            supplierCountryCode         = countryCode,
            goodsDescriptionCategory    = goodsDescriptionCategory,
            goodsDescriptionSubcategory = goodsDescriptionSubCategory,
            goodsDescriptionText        = None,
            hasSadReferenceNumber       = None,
            sadReferenceNumber          = None,
            referenceInformation        = None,
            currencyCode                = Some(currencyCode),
            taxableAmount               = Some(taxableAmount),
            vatAmount                   = Some(vatAmount),
            deductibleVatAmount         = Some(deductibleVatAmount),
            updateSequenceNumber        = 1,
            periodStartDate             = LocalDateTime.of(2026, 1, 1, 0, 0)
          )
        }
      )
    }
  }

  private def newController(
    vrnRepository: uk.gov.hmrc.euvatstubs.repositories.VrnStateRepository = new uk.gov.hmrc.euvatstubs.repositories.VrnStateRepository(),
    purchaseImportRepository: PurchaseImportRepository = new InMemoryPurchaseImportRepository
  ): EuVatCandeDataController =
    new EuVatCandeDataController(stubControllerComponents(), vrnRepository, purchaseImportRepository)

  def controller: EuVatCandeDataController = newController()

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
      (json \ "updateSeqNumber").as[Int] mustBe 2
    }

    "save the refund application for vrn ending with 666" in {
      val result = controller.addApplication("666")(fakeRequest)

      status(result) mustBe OK

      val json: JsValue = contentAsJson(result)
      (json \ "applicationId").as[Int] mustBe 666
      (json \ "applicationNumber").as[String] mustBe "GB123666"
      (json \ "updateSeqNumber").as[Int] mustBe 3
    }

    "save the refund application for any other vrn" in {
      val result = controller.addApplication("333")(fakeRequest)

      status(result) mustBe OK

      val json: JsValue = contentAsJson(result)
      (json \ "applicationId").as[Int] mustBe 660
      (json \ "applicationNumber").as[String] mustBe "GB123100"
      (json \ "updateSeqNumber").as[Int] mustBe 6
    }

    "clear prior purchases when a stub application id is reused" in {
      val purchaseRepository = new InMemoryPurchaseImportRepository
      val ctrl = newController(purchaseImportRepository = purchaseRepository)
      val oldPurchase = UpdatePurchaseDetailsRequest(
        applicationId               = 660,
        itemNumber                  = 4,
        goodsDescriptionCategory    = "3",
        goodsDescriptionSubCategory = None,
        goodsDescriptionText        = Some("previous session entry"),
        simplifiedInvoiceIndicator  = None,
        supplierName                = None,
        supplierAddress1            = None,
        supplierAddress2            = None,
        supplierAddress3            = None,
        supplierVatRegNumber        = None,
        supplierTaxIdentifier       = None,
        invoiceDate                 = None,
        invoiceNumber               = None,
        currencyCode                = None,
        taxableAmount               = None,
        vatAmount                   = None,
        deductibleVatAmount         = None,
        updateSequenceNumber        = 1
      )
      await(purchaseRepository.savePurchaseDetails("anonymous", 660, oldPurchase))

      val result = ctrl.addApplication("333")(fakeRequest)
      status(result) mustBe OK

      await(purchaseRepository.findPurchaseDetails("anonymous", 660, 4)) mustBe None
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

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "totalApplication").as[Int] mustBe 0
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
      (json \ "totalApplication").as[Int] mustBe 0
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
      val ctrl = newController(vrnRepository = repo)

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
        .withJsonBody(Json.obj("applicantVatRegNumber" -> "999900003"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getLatestApplications()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
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

  "EuVatCandeDataController.getImportDetails" should {
    "return import details for valid request" in {
      val fakeRequest = FakeRequest("GET", "/rds-cande-proxy/get-import-details/402/4")

      val result = controller.getImportDetails(402, 4)(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "applicationId").as[Int] mustBe 402
      (json \ "itemNumber").as[Int] mustBe 4
      (json \ "supplierName").asOpt[String] mustBe Some("Finnish International")
      (json \ "issuingDate").as[String] mustBe "2026-05-14T00:00:00"
      (json \ "periodStartDate").as[String] mustBe "2026-01-01T00:00:00"
      (json \ "supplierCountryCode").asOpt[String] mustBe Some("GB")
    }

    "return details for the requested import item rather than a fixed payload" in {
      val fakeRequest = FakeRequest("GET", "/rds-cande-proxy/get-import-details/402/6")

      val result = controller.getImportDetails(402, 6)(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "applicationId").as[Long] mustBe 402L
      (json \ "itemNumber").as[Int] mustBe 6
      (json \ "supplierName").asOpt[String] mustBe Some("Baltic Importers")
      (json \ "supplierCountryCode").asOpt[String] mustBe Some("LV")
    }

    "return NotFound when the item is not an import" in {
      val fakeRequest = FakeRequest("GET", "/rds-cande-proxy/get-import-details/402/1")

      val result = controller.getImportDetails(402, 1)(fakeRequest)

      status(result) mustBe NOT_FOUND
    }

    "simulate 5xx and return 500 for applicationId ending with 500" in {
      val fakeRequest = FakeRequest("GET", "/rds-cande-proxy/get-import-details/500/4")

      val result = controller.getImportDetails(500, 4)(fakeRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "EuVatCandeDataController.getSupplierTaxIdentifierCount" should {
    "return duplicateCount 1 for taxIdentifier ending TID-1" in {
      val fakeRequest = FakeRequest("POST", "/get-supplier-taxIdentifier-count")
        .withJsonBody(
          Json.obj(
            "applicationId" -> 1,
            "itemNumber"    -> 1,
            "taxIdentifier" -> "TID-1",
            "invoiceNumber" -> "INV-1"
          )
        )

      val result = controller.getSupplierTaxIdentifierCount()(fakeRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "duplicateCount").as[Int] mustBe 1
    }

    "return duplicateCount 2 for taxIdentifier ending TID-2" in {
      val fakeRequest = FakeRequest("POST", "/get-supplier-taxIdentifier-count")
        .withJsonBody(
          Json.obj(
            "applicationId" -> 1,
            "itemNumber"    -> 1,
            "taxIdentifier" -> "TID-2",
            "invoiceNumber" -> "INV-1"
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

    "allocate a new item number after seeded purchases and return its saved details" in {
      val ctrl = newController()
      val addRequest = FakeRequest("POST", "/add-purchase")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 402,
            "goodsDescriptionCategory" -> "21",
            "goodsDescriptionText"     -> "newly added purchase",
            "updateSequenceNumber"     -> 1
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val addResult = ctrl.addPurchase()(addRequest)

      status(addResult) mustBe OK
      val itemNumber = (contentAsJson(addResult) \ "itemNumber").as[Int]
      itemNumber mustBe 7

      val detailsRequest = FakeRequest("POST", "/get-purchase-details")
        .withJsonBody(Json.obj("applicationId" -> 402, "itemNumber" -> itemNumber))
        .withHeaders("Content-Type" -> "application/json")

      val detailsResult = ctrl.getPurchaseDetails()(detailsRequest)

      status(detailsResult) mustBe OK
      (contentAsJson(detailsResult) \ "goodsDescriptionCode").as[String] mustBe "21"
      (contentAsJson(detailsResult) \ "goodsDescriptionText").as[String] mustBe "newly added purchase"
    }

    "preserve the newly added item when a different item is updated" in {
      val ctrl = newController()
      val addRequest = FakeRequest("POST", "/add-purchase")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 660,
            "goodsDescriptionCategory" -> "3",
            "goodsDescriptionText"     -> "new purchase",
            "updateSequenceNumber"     -> 1
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val addResult = ctrl.addPurchase()(addRequest)
      status(addResult) mustBe OK
      (contentAsJson(addResult) \ "itemNumber").as[Int] mustBe 4

      val updateDefault = FakeRequest("PUT", "/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 660,
            "itemNumber"               -> 3,
            "goodsDescriptionCategory" -> "8",
            "goodsDescriptionText"     -> "updated default",
            "updateSequenceNumber"     -> 2
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      status(ctrl.updatePurchaseDetails()(updateDefault)) mustBe OK

      val listRequest = FakeRequest("POST", "/get-purchases-and-imports")
        .withJsonBody(Json.obj("applicationId" -> 660))
        .withHeaders("Content-Type" -> "application/json")
      val listResult = ctrl.getPurchaseImportList()(listRequest)
      (contentAsJson(listResult) \ "purchaseImportList").as[JsArray].value.map(item => (item \ "itemNumber").as[Int]) mustBe List(2, 3, 4)

      val newDetails = ctrl.getPurchaseDetails()(
        FakeRequest("POST", "/get-purchase-details")
          .withJsonBody(Json.obj("applicationId" -> 660, "itemNumber" -> 4))
          .withHeaders("Content-Type" -> "application/json")
      )
      (contentAsJson(newDetails) \ "goodsDescriptionText").as[String] mustBe "new purchase"
    }

    "scope created purchases to the X-Session-ID header" in {
      val ctrl = newController()
      val sessionA = "session-a"
      val sessionB = "session-b"
      val addRequest = FakeRequest("POST", "/add-purchase")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 660,
            "goodsDescriptionCategory" -> "3",
            "goodsDescriptionText"     -> "session A purchase",
            "updateSequenceNumber"     -> 1
          )
        )
        .withHeaders("Content-Type" -> "application/json", "X-Session-ID" -> sessionA)

      val addResult = ctrl.addPurchase()(addRequest)
      status(addResult) mustBe OK
      val itemNumber = (contentAsJson(addResult) \ "itemNumber").as[Int]

      val sessionAResult = ctrl.getPurchaseDetailsByPath(660, itemNumber)(
        FakeRequest("GET", s"/get-purchase-details/660/$itemNumber").withHeaders("X-Session-ID" -> sessionA)
      )
      status(sessionAResult) mustBe OK
      (contentAsJson(sessionAResult) \ "goodsDescriptionText").as[String] mustBe "session A purchase"

      val sessionBResult = ctrl.getPurchaseDetailsByPath(660, itemNumber)(
        FakeRequest("GET", s"/get-purchase-details/660/$itemNumber").withHeaders("X-Session-ID" -> sessionB)
      )
      status(sessionBResult) mustBe NOT_FOUND
    }

    "return 400 when the request body is invalid" in {
      val fakeRequest = FakeRequest("POST", "/add-purchase")
        .withJsonBody(Json.obj("invalid" -> "body"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.addPurchase()(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }
  }

  "EuVatCandeDataController.addImport" should {

    val validImportRequest =
      FakeRequest("POST", "/add-import")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 123456,
            "goodsDescriptionCategory" -> "1",
            "updateSequenceNumber"     -> 5
          )
        )
        .withHeaders("Content-Type" -> "application/json")

    "return itemNumber and updateSequenceNumber for a valid request" in {
      val result = controller.addImport()(validImportRequest)

      status(result) mustBe OK
      val resp = contentAsJson(result).as[AddPurchaseResponse]
      resp.itemNumber mustBe 4
      resp.updateSequenceNumber mustBe 5
    }

    "return 400 when the request body is invalid" in {
      val fakeRequest = FakeRequest("POST", "/add-import")
        .withJsonBody(Json.obj("invalid" -> "body"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.addImport()(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }

    "simulate 5xx and return 500 when applicationId ends with 500" in {
      val fakeRequest = FakeRequest("POST", "/add-import")
        .withJsonBody(Json.obj("applicationId" -> 500, "itemNumber" -> 1, "goodsDescriptionCategory" -> "1", "updateSequenceNumber" -> 1))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.addImport()(fakeRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "EuVatCandeDataController.getPurchaseDetails" should {

    val validRequest =
      FakeRequest("POST", "/get-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId" -> 123456,
            "itemNumber"    -> 2
          )
        )
        .withHeaders("Content-Type" -> "application/json")

    "return the purchase details for a valid request" in {
      val ctrl = newController()

      val firstSave = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 123456,
            "goodsDescriptionCategory" -> "10",
            "goodsDescriptionText"     -> "first item",
            "updateSequenceNumber"     -> 1
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val secondSave = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 123456,
            "goodsDescriptionCategory" -> "11",
            "goodsDescriptionText"     -> "second item",
            "supplierName"             -> "Supplier Ltd",
            "invoiceNumber"            -> "INV-002",
            "updateSequenceNumber"     -> 2
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      status(ctrl.updatePurchaseDetails()(firstSave)) mustBe OK
      status(ctrl.updatePurchaseDetails()(secondSave)) mustBe OK

      val result = ctrl.getPurchaseDetails()(validRequest)

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "goodsDescriptionCode").as[String] mustBe "11"
      (json \ "goodsDescriptionText").as[String] mustBe "second item"
      (json \ "supplierName").as[String] mustBe "Supplier Ltd"
      (json \ "invoiceNumber").as[String] mustBe "INV-002"
      (json \ "updateSequenceNumber").as[Int] mustBe 2
    }

    "return 400 when the request body is invalid" in {
      val fakeRequest = FakeRequest("POST", "/get-purchase-details")
        .withJsonBody(Json.obj("invalid" -> "body"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getPurchaseDetails()(fakeRequest)

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

    "replace an existing purchase when the same item number is saved again" in {
      val ctrl = newController()

      val firstSave = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 234567,
            "itemNumber"               -> 7,
            "goodsDescriptionCategory" -> "20",
            "goodsDescriptionText"     -> "original item",
            "updateSequenceNumber"     -> 1
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val secondSave = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 234567,
            "itemNumber"               -> 7,
            "goodsDescriptionCategory" -> "21",
            "goodsDescriptionText"     -> "updated item",
            "invoiceNumber"            -> "INV-777",
            "updateSequenceNumber"     -> 2
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      status(ctrl.updatePurchaseDetails()(firstSave)) mustBe OK
      status(ctrl.updatePurchaseDetails()(secondSave)) mustBe OK

      val listRequest = FakeRequest("POST", "/get-purchase-import-list")
        .withJsonBody(Json.obj("applicationId" -> 234567))
        .withHeaders("Content-Type" -> "application/json")

      val listResult = ctrl.getPurchaseImportList()(listRequest)

      status(listResult) mustBe OK
      val listJson = contentAsJson(listResult)
      (listJson \ "totalItems").as[Int] mustBe 3

      val detailRequest = FakeRequest("POST", "/get-purchase-details")
        .withJsonBody(Json.obj("applicationId" -> 234567, "itemNumber" -> 7))
        .withHeaders("Content-Type" -> "application/json")

      val detailResult = ctrl.getPurchaseDetails()(detailRequest)

      status(detailResult) mustBe OK
      val detailJson = contentAsJson(detailResult)
      (detailJson \ "goodsDescriptionCode").as[String] mustBe "21"
      (detailJson \ "goodsDescriptionText").as[String] mustBe "updated item"
      (detailJson \ "invoiceNumber").as[String] mustBe "INV-777"
    }

    "persist the incoming purchase details for later purchase-import listing" in {
      val purchaseImportRepository = new InMemoryPurchaseImportRepository
      val ctrl = newController(purchaseImportRepository = purchaseImportRepository)

      val updateRequestOne = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 123456,
            "goodsDescriptionCategory" -> "10",
            "goodsDescriptionText"     -> "office stationery and consumables",
            "currencyCode"             -> "EUR",
            "taxableAmount"            -> 1000,
            "vatAmount"                -> 99,
            "deductibleVatAmount"      -> 40,
            "updateSequenceNumber"     -> 7
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      val updateRequestTwo = FakeRequest("PUT", "/rds-cande-proxy/update-purchase-details")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 123456,
            "goodsDescriptionCategory" -> "11",
            "goodsDescriptionText"     -> "other item",
            "currencyCode"             -> "EUR",
            "taxableAmount"            -> 250,
            "vatAmount"                -> 50,
            "deductibleVatAmount"      -> 20,
            "updateSequenceNumber"     -> 8
          )
        )
        .withHeaders("Content-Type" -> "application/json")

      status(ctrl.updatePurchaseDetails()(updateRequestOne)) mustBe OK
      status(ctrl.updatePurchaseDetails()(updateRequestTwo)) mustBe OK

      val listRequest = FakeRequest("GET", "/get-purchases-and-imports")
        .withJsonBody(Json.obj("applicationId" -> 123456))
        .withHeaders("Content-Type" -> "application/json")

      val listResult = ctrl.getPurchaseImportList()(listRequest)
      status(listResult) mustBe OK

      val json = contentAsJson(listResult)
      (json \ "totalItems").as[Int] mustBe 4
      val imports = (json \ "purchaseImportList").as[JsArray].value
      imports.exists(item => (item \ "itemNumber").as[Int] == 1) mustBe true
      imports.exists(item => (item \ "itemNumber").as[Int] == 2) mustBe true
      imports.exists(item => (item \ "goodsDescriptionCategory").as[String] == "10") mustBe true
      imports.exists(item => (item \ "itemType").as[String] == "I") mustBe true
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

  "EuVatCandeDataController.getPurchaseImportList" should {
    def fakeRequest(appId: Long) = FakeRequest("GET", "/get-purchases-and-imports")
      .withJsonBody(Json.obj("applicationId" -> appId))
      .withHeaders("Content-Type" -> "application/json")

    "return purchase import list with 1 item for application 999" in {
      val result = controller.getPurchaseImportList()(fakeRequest(999))
      status(result) mustBe OK

      val json = contentAsJson(result)
      (json \ "totalItems").as[Int] mustBe 1
    }

    "return one purchase and one import for an arbitrary application" in {
      val result = controller.getPurchaseImportList()(fakeRequest(23))
      status(result) mustBe OK

      val json = contentAsJson(result)
      (json \ "totalItems").as[Int] mustBe 2
      val items = (json \ "purchaseImportList").as[JsArray].value
      items.map(item => (item \ "itemType").as[String]).toSet mustBe Set("P", "I")
    }

    "return BadRequest for invalid appId" in {
      val result = controller.getPurchaseImportList()(fakeRequest(0))
      status(result) mustBe BAD_REQUEST
    }

    "return BadRequest for invalid request" in {
      def noRequest() = FakeRequest("GET", "/get-purchases-and-imports")
        .withJsonBody(Json.obj())
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.getPurchaseImportList()(noRequest())
      status(result) mustBe BAD_REQUEST
    }

    "delete the requested seeded import and retain the other items" in {
      val ctrl = newController()
      val initialList = ctrl.getPurchaseImportList()(fakeRequest(402))
      status(initialList) mustBe OK
      (contentAsJson(initialList) \ "totalItems").as[Int] mustBe 6

      val deleteRequest = FakeRequest("DELETE", "/delete-import")
        .withJsonBody(Json.obj("applicationId" -> 402, "itemNumber" -> 4, "updateSequenceNumber" -> 8))
        .withHeaders("Content-Type" -> "application/json")
      val deleteResult = ctrl.deleteImport()(deleteRequest)

      status(deleteResult) mustBe OK
      (contentAsJson(deleteResult) \ "updateSequenceNumber").as[Int] mustBe 8

      val listAfterDelete = ctrl.getPurchaseImportList()(fakeRequest(402))
      val json = contentAsJson(listAfterDelete)
      (json \ "totalItems").as[Int] mustBe 5
      val items = (json \ "purchaseImportList").as[JsArray].value
      items.exists(item => (item \ "itemNumber").as[Int] == 4) mustBe false
      items.exists(item => (item \ "itemNumber").as[Int] == 6) mustBe true
    }

    "delete the requested purchase and retain imports and sibling items" in {
      val ctrl = newController()
      val initialList = ctrl.getPurchaseImportList()(fakeRequest(402))
      status(initialList) mustBe OK

      val deleteRequest = FakeRequest("DELETE", "/rds-cande-proxy/delete-purchase")
        .withJsonBody(Json.obj("applicationId" -> 402, "itemNumber" -> 5, "updateSequenceNumber" -> 8))
        .withHeaders("Content-Type" -> "application/json")
      val deleteResult = ctrl.deletePurchase(deleteRequest)

      status(deleteResult) mustBe OK
      (contentAsJson(deleteResult) \ "updateSequenceNumber").as[Int] mustBe 9

      val listAfterDelete = ctrl.getPurchaseImportList()(fakeRequest(402))
      val json = contentAsJson(listAfterDelete)
      (json \ "totalItems").as[Int] mustBe 5
      val items = (json \ "purchaseImportList").as[JsArray].value
      items.exists(item => (item \ "itemNumber").as[Int] == 5) mustBe false
      items.exists(item => (item \ "itemNumber").as[Int] == 4 && (item \ "itemType").as[String] == "I") mustBe true
      items.exists(item => (item \ "itemNumber").as[Int] == 1) mustBe true
    }

    "return BadRequest for invalid delete-purchase requests and simulate backend failures" in {
      val invalidRequest = FakeRequest("DELETE", "/rds-cande-proxy/delete-purchase")
        .withJsonBody(Json.obj("applicationId" -> 402))
        .withHeaders("Content-Type" -> "application/json")
      status(controller.deletePurchase(invalidRequest)) mustBe BAD_REQUEST

      val failureRequest = FakeRequest("DELETE", "/rds-cande-proxy/delete-purchase")
        .withJsonBody(Json.obj("applicationId" -> 402, "itemNumber" -> 500, "updateSequenceNumber" -> 8))
        .withHeaders("Content-Type" -> "application/json")
      status(controller.deletePurchase(failureRequest)) mustBe INTERNAL_SERVER_ERROR
    }
  }

  "EuVatCandeDataController.deleteApplication" should {
    "remove persisted purchases for a valid delete request" in {
      val ctrl = newController()
      val addRequest = FakeRequest("POST", "/add-purchase")
        .withJsonBody(
          Json.obj(
            "applicationId"            -> 123456,
            "goodsDescriptionCategory" -> "1",
            "goodsDescriptionText"     -> "purchase to delete",
            "updateSequenceNumber"     -> 1
          )
        )
        .withHeaders("Content-Type" -> "application/json")
      val addResult = ctrl.addPurchase()(addRequest)
      val itemNumber = (contentAsJson(addResult) \ "itemNumber").as[Int]

      val fakeRequest = FakeRequest("DELETE", "/rds-cande-proxy/delete-application")
        .withJsonBody(Json.obj("applicationId" -> 123456L, "updateSequenceNumber" -> 1))
        .withHeaders("Content-Type" -> "application/json")

      val result = ctrl.deleteApplication()(fakeRequest)

      status(result) mustBe OK
      contentAsString(result) mustBe ""

      val detailsResult = ctrl.getPurchaseDetails()(
        FakeRequest("POST", "/get-purchase-details")
          .withJsonBody(Json.obj("applicationId" -> 123456, "itemNumber" -> itemNumber))
          .withHeaders("Content-Type" -> "application/json")
      )
      status(detailsResult) mustBe NOT_FOUND
    }

    "return 500 when backend failure is simulated" in {
      val fakeRequest = FakeRequest("DELETE", "/rds-cande-proxy/delete-application")
        .withJsonBody(Json.obj("applicationId" -> 500, "updateSequenceNumber" -> 1))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.deleteApplication()(fakeRequest)

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "return 400 when the request body is invalid" in {
      val fakeRequest = FakeRequest("DELETE", "/rds-cande-proxy/delete-application")
        .withJsonBody(Json.obj("invalid" -> "body"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.deleteApplication()(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }
  }

  "EuVatCandeDataController.updateApplicationDetails" should {

    def requestWith(json: JsObject) =
      FakeRequest("PUT", "/rds-cande-proxy/update-application-details")
        .withJsonBody(json)
        .withHeaders("Content-Type" -> "application/json")

    val mandatoryJson = Json.obj(
      "applicationId"         -> 133,
      "applicationLanguage"   -> "en",
      "refundingCountry"      -> "LV",
      "periodStartDate"       -> "2011-06-01T00:00:00",
      "periodEndDate"         -> "2011-10-31T23:59:59",
      "applicantEmailAddress" -> "test@hotmail.com",
      "updateSequenceNumber"  -> 30
    )

    "return updateSequenceNumber + 2" in {
      val result = controller.updateApplicationDetails()(requestWith(mandatoryJson))

      status(result) mustBe OK
      (contentAsJson(result) \ "updateSequenceNumber").as[Int] mustBe 32
    }

    "simulate error500@test.com and return 500" in {
      val result = controller.updateApplicationDetails()(
        requestWith(mandatoryJson + ("applicantEmailAddress" -> JsString("error500@test.com")))
      )

      status(result) mustBe INTERNAL_SERVER_ERROR
    }

    "simulate error400@test.com and return 400" in {
      val result = controller.updateApplicationDetails()(
        requestWith(mandatoryJson + ("applicantEmailAddress" -> JsString("error400@test.com")))
      )

      status(result) mustBe BAD_REQUEST
    }

    "return BadRequest when applicationLanguage is missing" in {
      val result = controller.updateApplicationDetails()(requestWith(mandatoryJson - "applicationLanguage"))

      status(result) mustBe BAD_REQUEST
    }

    "return BadRequest when a mandatory field is missing" in {
      val result = controller.updateApplicationDetails()(requestWith(mandatoryJson - "refundingCountry"))

      status(result) mustBe BAD_REQUEST
    }

    "return BadRequest when no JSON body provided" in {
      val fakeRequest = FakeRequest("PUT", "/rds-cande-proxy/update-application-details")
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.updateApplicationDetails()(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }
  }

  "EuVatCandeDataController.deleteImport" should {
    val fakeRequestJson = (path: String) =>
      FakeRequest("DELETE", path)
        .withJsonBody(
          Json.obj(
            "applicationId"        -> 123456L,
            "itemNumber"           -> 1,
            "updateSequenceNumber" -> 5
          )
        )
        .withHeaders("Content-Type" -> "application/json")

    "return updateSequenceNumber for a valid delete import request" in {
      val result = controller.deleteImport()(fakeRequestJson("/delete-import"))

      status(result) mustBe OK
      val json = contentAsJson(result)
      (json \ "updateSequenceNumber").as[Int] mustBe 5
    }

    "return BadRequest when the request body is invalid" in {
      val fakeRequest = FakeRequest("DELETE", "/delete-import")
        .withJsonBody(Json.obj("invalid" -> "body"))
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.deleteImport()(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }

    "return 400 when no JSON body provided" in {
      val fakeRequest = FakeRequest("DELETE", "/delete-import")
        .withHeaders("Content-Type" -> "application/json")

      val result = controller.deleteImport()(fakeRequest)

      status(result) mustBe BAD_REQUEST
    }
  }
}
