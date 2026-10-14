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

import org.scalatestplus.play.PlaySpec
import play.api.libs.json.Json
import uk.gov.hmrc.euvatstubs.models.PurchaseImport
import uk.gov.hmrc.euvatstubs.models.requests.UpdatePurchaseDetailsRequest

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class InMemoryPurchaseImportRepositorySpec extends PlaySpec {

  private def request(
    applicationId: Long,
    itemNumber: Int,
    category: String,
    currencyCode: Option[String] = Some("EUR"),
    taxableAmount: Option[BigDecimal] = Some(BigDecimal(100)),
    vatAmount: Option[BigDecimal] = Some(BigDecimal(20)),
    deductibleVatAmount: Option[BigDecimal] = Some(BigDecimal(20))
  ): UpdatePurchaseDetailsRequest =
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
      currencyCode                = currencyCode,
      taxableAmount               = taxableAmount,
      vatAmount                   = vatAmount,
      deductibleVatAmount         = deductibleVatAmount,
      updateSequenceNumber        = 1
    )

  private def await[A](value: scala.concurrent.Future[A]): A = Await.result(value, Duration.Inf)
  private val sessionId = "session-one"

  "InMemoryPurchaseImportRepository" should {
    "assign increasing item numbers and replace an existing item" in {
      val repository = new InMemoryPurchaseImportRepository

      val first = await(repository.savePurchaseDetails(sessionId, 123, request(123, 0, "first")))
      val second = await(repository.savePurchaseDetails(sessionId, 123, request(123, 0, "second")))
      val updated = await(repository.savePurchaseDetails(sessionId, 123, request(123, 1, "updated")))

      first.itemNumber mustBe 1
      second.itemNumber mustBe 2
      updated.itemNumber mustBe 1
      await(repository.findPurchaseDetails(sessionId, 123, 1)).map(_.goodsDescriptionCategory) mustBe Some("updated")
      await(repository.findPurchaseDetails(sessionId, 123, 99)) mustBe None
      await(repository.findPurchaseImports(sessionId, 123)).map(_.goodsDescriptionCategory) mustBe List("second", "updated")
      await(repository.findPurchaseDetails(sessionId, 456, 1)) mustBe None
    }

    "isolate purchases with the same application and item numbers by session" in {
      val repository = new InMemoryPurchaseImportRepository

      await(repository.savePurchaseDetails("session-one", 123, request(123, 4, "first session")))
      await(repository.savePurchaseDetails("session-two", 123, request(123, 4, "second session")))

      await(repository.findPurchaseDetails("session-one", 123, 4)).map(_.goodsDescriptionCategory) mustBe Some("first session")
      await(repository.findPurchaseDetails("session-two", 123, 4)).map(_.goodsDescriptionCategory) mustBe Some("second session")
    }

    "seed the known default applications once and preserve their list values" in {
      val repository = new InMemoryPurchaseImportRepository

      await(repository.seedDefaultPurchaseDetails(sessionId, 111))
      await(repository.seedDefaultPurchaseDetails(sessionId, 999))
      await(repository.seedDefaultPurchaseDetails(sessionId, 402))
      await(repository.seedDefaultPurchaseDetails(sessionId, 660))
      await(repository.seedDefaultPurchaseDetails(sessionId, 402))

      await(repository.findPurchaseImports(sessionId, 111)).map(_.itemNumber) mustBe List(1)
      await(repository.findPurchaseImports(sessionId, 999)).map(_.itemType) mustBe List("I")

      val mixedDefaults = await(repository.findPurchaseImports(sessionId, 402))
      mixedDefaults.map(_.itemNumber) mustBe List(4, 1, 5, 2, 6, 3)
      mixedDefaults.find(_.itemNumber == 4).map(_.itemType) mustBe Some("I")
      mixedDefaults.find(_.itemNumber == 2).map(_.deductibleVatAmount) mustBe Some(BigDecimal("123.45"))

      val fallbackDefault = await(repository.findPurchaseImports(sessionId, 660)).head
      fallbackDefault.goodsDescriptionCategory mustBe "7"
      fallbackDefault.deductibleVatAmount mustBe BigDecimal("111.11")
    }

    "map absent optional amounts to zero in purchase imports" in {
      val repository = new InMemoryPurchaseImportRepository
      await(repository.savePurchaseDetails(sessionId, 321, request(321, 1, "empty", None, None, None, None)))

      val purchaseImport = await(repository.findPurchaseImports(sessionId, 321)).head
      purchaseImport.currencyCode mustBe ""
      purchaseImport.taxableAmount mustBe BigDecimal(0)
      purchaseImport.vatAmount mustBe BigDecimal(0)
      purchaseImport.deductibleVatAmount mustBe BigDecimal(0)
    }
  }

  "StoredPurchaseDetails.format" should {
    "round-trip the current stored format" in {
      val purchaseDetails = request(123, 2, "current")
      val purchaseImport = PurchaseImport(2, "current", None, "EUR", 100, 20, 20, "P")
      val stored = StoredPurchaseDetails(123, purchaseDetails, purchaseImport)

      StoredPurchaseDetails.format.reads(Json.toJson(stored)) mustBe play.api.libs.json.JsSuccess(stored)
    }

    "read legacy purchase-import documents as purchase details" in {
      val purchaseImport = PurchaseImport(4, "legacy", Some("sub"), "GBP", 250, 50, 25, "I")
      val legacyDocument = Json.obj(
        "applicationId"  -> 456,
        "purchaseImport" -> Json.toJson(purchaseImport)
      )

      val result = StoredPurchaseDetails.format.reads(legacyDocument)

      result.get.purchaseDetails.applicationId mustBe 456
      result.get.purchaseDetails.itemNumber mustBe 4
      result.get.purchaseDetails.goodsDescriptionCategory mustBe "legacy"
      result.get.purchaseDetails.currencyCode mustBe Some("GBP")
      result.get.purchaseDetails.taxableAmount mustBe Some(BigDecimal(250))
      result.get.purchaseImport mustBe purchaseImport
    }

    "reject documents without either stored purchase representation" in {
      StoredPurchaseDetails.format.reads(Json.obj("applicationId" -> 456)).isError mustBe true
    }
  }
}
