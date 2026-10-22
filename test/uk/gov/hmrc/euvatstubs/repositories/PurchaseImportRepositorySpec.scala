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

package uk.gov.hmrc.euvatstubs.repositories

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import uk.gov.hmrc.euvatstubs.models.PurchaseImport
import uk.gov.hmrc.euvatstubs.models.requests.UpdatePurchaseDetailsRequest
import uk.gov.hmrc.euvatstubs.repositories.InMemoryPurchaseImportRepository as SessionRepository

import scala.concurrent.Future

class PurchaseImportRepositorySpec extends AnyWordSpec with Matchers with ScalaFutures {

  private class InMemoryPurchaseImportRepository extends SessionRepository {
    private val session = "repository-spec"

    def savePurchaseDetails(applicationId: Long, details: UpdatePurchaseDetailsRequest): Future[UpdatePurchaseDetailsRequest] =
      super.savePurchaseDetails(session, applicationId, details)

    def findPurchaseDetails(applicationId: Long, itemNumber: Int): Future[Option[UpdatePurchaseDetailsRequest]] =
      super.findPurchaseDetails(session, applicationId, itemNumber)

    def findImportDetails(applicationId: Long, itemNumber: Int) =
      super.findImportDetails(session, applicationId, itemNumber)

    def findPurchaseImports(applicationId: Long): Future[List[PurchaseImport]] =
      super.findPurchaseImports(session, applicationId)

    def seedDefaultPurchaseDetails(applicationId: Long): Future[Unit] =
      super.seedDefaultPurchaseDetails(session, applicationId)

    def deleteImport(applicationId: Long, itemNumber: Int): Future[Unit] =
      super.deleteImport(session, applicationId, itemNumber)
  }

  "InMemoryPurchaseImportRepository" should {
    "keep a deleted seeded import deleted on subsequent list requests" in {
      val repository = new InMemoryPurchaseImportRepository
      repository.seedDefaultPurchaseDetails(999)
      repository.findPurchaseImports(999).futureValue.map(_.itemNumber) shouldBe List(2)

      repository.deleteImport(999, 2)
      repository.seedDefaultPurchaseDetails(999)

      repository.findPurchaseImports(999).futureValue shouldBe empty
    }

    "delete only the requested import and retain sibling items" in {
      val repository = new InMemoryPurchaseImportRepository
      repository.seedDefaultPurchaseDetails(402).futureValue

      repository.deleteImport(402, 4).futureValue

      val itemNumbers = repository.findPurchaseImports(402).futureValue.map(_.itemNumber)
      itemNumbers should not contain 4
      itemNumbers should contain(6)
      itemNumbers should have size 5
    }

    "save, replace, and retrieve purchase details" in {
      val repository = new InMemoryPurchaseImportRepository
      val first = repository.savePurchaseDetails(123456, purchaseDetails(123456, 0, "10")).futureValue
      val replaced = repository.savePurchaseDetails(123456, purchaseDetails(123456, first.itemNumber, "11")).futureValue
      val added = repository.savePurchaseDetails(123456, purchaseDetails(123456, 0, "12")).futureValue

      replaced.itemNumber                                                                                  shouldBe first.itemNumber
      added.itemNumber                                                                                     shouldBe first.itemNumber + 1
      repository.findPurchaseDetails(123456, first.itemNumber).futureValue.map(_.goodsDescriptionCategory) shouldBe Some("11")
      repository.findPurchaseDetails(123456, added.itemNumber).futureValue.map(_.goodsDescriptionCategory) shouldBe Some("12")
      repository.findPurchaseDetails(123456, 99).futureValue                                               shouldBe None
      repository.findPurchaseImports(123456).futureValue                                                     should have size 2
    }

    "seed the configured purchase fixture for application 111" in {
      val repository = new InMemoryPurchaseImportRepository

      repository.seedDefaultPurchaseDetails(111)

      val items = repository.findPurchaseImports(111).futureValue
      items.map(_.itemNumber) shouldBe List(1)
      items.map(_.itemType)   shouldBe List("P")
    }

    "seed one purchase and one import for an arbitrary application" in {
      val repository = new InMemoryPurchaseImportRepository

      repository.seedDefaultPurchaseDetails(23)

      val items = repository.findPurchaseImports(23).futureValue
      items.map(_.itemType).toSet                                       shouldBe Set("P", "I")
      items.map(_.itemNumber).toSet                                     shouldBe Set(2, 3)
      repository.findImportDetails(23, 2).futureValue.map(_.itemNumber) shouldBe Some(2)
    }

    "seed the default import when an added purchase already uses item number 4" in {
      val repository = new InMemoryPurchaseImportRepository
      repository.savePurchaseDetails(23, purchaseDetails(23, 4, "8")).futureValue

      repository.seedDefaultPurchaseDetails(23).futureValue

      val items = repository.findPurchaseImports(23).futureValue
      items.find(_.itemType == "I").map(_.itemNumber)                                                  shouldBe Some(2)
      items.find(item => item.itemType == "P" && item.itemNumber == 4).map(_.goodsDescriptionCategory) shouldBe Some("8")
    }

    "not recreate a default import after it has been deleted" in {
      val repository = new InMemoryPurchaseImportRepository
      repository.seedDefaultPurchaseDetails(23).futureValue

      repository.deleteImport(23, 2).futureValue
      repository.seedDefaultPurchaseDetails(23).futureValue

      val items = repository.findPurchaseImports(23).futureValue
      items.map(_.itemType) shouldBe List("P")
    }

    "find import details by application and item number" in {
      val repository = new InMemoryPurchaseImportRepository
      repository.seedDefaultPurchaseDetails(402).futureValue

      repository.findImportDetails(402, 4).futureValue.map(_.supplierName) shouldBe Some(Some("Finnish International"))
      repository.findImportDetails(402, 6).futureValue.map(_.supplierName) shouldBe Some(Some("Baltic Importers"))
      repository.findImportDetails(402, 1).futureValue                     shouldBe None
      repository.findImportDetails(999, 4).futureValue                     shouldBe None
    }
  }

  "StoredPurchaseDetails JSON format" should {
    "read a legacy import-only record and write the expanded record" in {
      val purchaseImport = PurchaseImport(
        itemNumber                  = 3,
        itemType                    = "I",
        goodsDescriptionCategory    = "9",
        goodsDescriptionSubCategory = Some("9.1"),
        currencyCode                = "EUR",
        taxableAmount               = BigDecimal(100),
        vatAmount                   = BigDecimal(20),
        deductibleVatAmount         = BigDecimal(10)
      )
      val legacyJson = Json.obj("applicationId" -> 123L, "purchaseImport" -> Json.toJson(purchaseImport))

      val stored = legacyJson.as[StoredPurchaseDetails]

      stored.purchaseDetails.applicationId          shouldBe 123L
      stored.purchaseDetails.itemNumber             shouldBe 3
      stored.purchaseImport.itemType                shouldBe "I"
      Json.toJson(stored).as[StoredPurchaseDetails] shouldBe stored
    }

    "create a purchase import when reading a details-only record" in {
      val details = purchaseDetails(123, 4, "8")
      val legacyJson = Json.obj("applicationId" -> 123L, "purchaseDetails" -> Json.toJson(details))

      val stored = legacyJson.as[StoredPurchaseDetails]

      stored.purchaseDetails                         shouldBe details
      stored.purchaseImport.itemType                 shouldBe "P"
      stored.purchaseImport.goodsDescriptionCategory shouldBe "8"
      stored.purchaseImport.currencyCode             shouldBe "EUR"
    }
  }

  private def purchaseDetails(applicationId: Long, itemNumber: Int, category: String): UpdatePurchaseDetailsRequest =
    UpdatePurchaseDetailsRequest(
      applicationId               = applicationId,
      itemNumber                  = itemNumber,
      goodsDescriptionCategory    = category,
      goodsDescriptionSubCategory = None,
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
      currencyCode                = Some("EUR"),
      taxableAmount               = Some(BigDecimal(100)),
      vatAmount                   = Some(BigDecimal(20)),
      deductibleVatAmount         = Some(BigDecimal(10)),
      updateSequenceNumber        = 1
    )
}
