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

import play.api.Logging
import uk.gov.hmrc.euvatstubs.models.PurchaseImport
import uk.gov.hmrc.euvatstubs.models.requests.UpdatePurchaseDetailsRequest

import javax.inject.Singleton
import scala.collection.mutable
import scala.concurrent.Future

@Singleton
class InMemoryPurchaseImportRepository extends PurchaseImportRepository with Logging {

  private val data = mutable.Map.empty[(String, Long), Vector[StoredPurchaseDetails]]

  override def savePurchaseDetails(sessionId: String,
                                   applicationId: Long,
                                   purchaseDetails: UpdatePurchaseDetailsRequest
                                  ): Future[UpdatePurchaseDetailsRequest] =
    Future.successful {
      data.synchronized {
        val key = sessionId -> applicationId
        val existing = data.getOrElse(key, Vector.empty)
        val assignedItemNumber =
          if (purchaseDetails.itemNumber > 0 && existing.exists(_.purchaseDetails.itemNumber == purchaseDetails.itemNumber))
            purchaseDetails.itemNumber
          else if (purchaseDetails.itemNumber > 0) purchaseDetails.itemNumber
          else existing.map(_.purchaseDetails.itemNumber).foldLeft(0)(Math.max) + 1

        val savedDetails = purchaseDetails.copy(applicationId = applicationId, itemNumber = assignedItemNumber)
        val itemType = existing.find(_.purchaseDetails.itemNumber == assignedItemNumber).map(_.purchaseImport.itemType).getOrElse("P")
        val storedEntry = StoredPurchaseDetails(applicationId, savedDetails, toPurchaseImport(savedDetails, itemType), sessionId)

        data.update(key, existing.filterNot(_.purchaseDetails.itemNumber == assignedItemNumber) :+ storedEntry)
        savedDetails
      }
    }

  override def findPurchaseDetails(sessionId: String, applicationId: Long, itemNumber: Int): Future[Option[UpdatePurchaseDetailsRequest]] =
    Future.successful {
      data.synchronized {
        data.getOrElse(sessionId -> applicationId, Vector.empty).find(_.purchaseDetails.itemNumber == itemNumber).map(_.purchaseDetails)
      }
    }

  override def findPurchaseImports(sessionId: String, applicationId: Long): Future[List[PurchaseImport]] = Future.successful {
    data.synchronized {
      data.getOrElse(sessionId -> applicationId, Vector.empty).map(_.purchaseImport).toList
    }
  }

  override def deletePurchaseDetails(sessionId: String, applicationId: Long): Future[Unit] = Future.successful {
    data.synchronized {
      data.remove(sessionId -> applicationId)
      ()
    }
  }

  override def seedDefaultPurchaseDetails(sessionId: String, applicationId: Long): Future[Unit] = Future.successful {
    data.synchronized {
      val key = sessionId -> applicationId
      val existing = data.getOrElse(key, Vector.empty)
      val existingItemNumbers = existing.map(_.purchaseDetails.itemNumber).toSet
      val seeds = defaultEntries(applicationId)
        .filterNot(entry => existingItemNumbers.contains(entry.purchaseDetails.itemNumber))
        .map(_.copy(sessionKey = sessionId))

      if (seeds.nonEmpty) data.update(key, existing ++ seeds)
    }
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
    case _ => Vector(storedDefault(applicationId, 3, "P", goodsDescriptionCategory = "7", deductibleVatAmount = 111.11))
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

    StoredPurchaseDetails(applicationId, purchaseDetails, toPurchaseImport(purchaseDetails, itemType))
  }

  private def toPurchaseImport(purchaseDetails: UpdatePurchaseDetailsRequest, itemType: String): PurchaseImport =
    PurchaseImport(
      itemNumber                  = purchaseDetails.itemNumber,
      itemType                    = itemType,
      goodsDescriptionCategory    = purchaseDetails.goodsDescriptionCategory,
      goodsDescriptionSubCategory = purchaseDetails.goodsDescriptionSubCategory,
      currencyCode                = purchaseDetails.currencyCode.getOrElse(""),
      taxableAmount               = purchaseDetails.taxableAmount.getOrElse(BigDecimal(0)),
      vatAmount                   = purchaseDetails.vatAmount.getOrElse(BigDecimal(0)),
      deductibleVatAmount         = purchaseDetails.deductibleVatAmount.getOrElse(BigDecimal(0))
    )
}
