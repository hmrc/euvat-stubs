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
import uk.gov.hmrc.euvatstubs.models.responses.GetImportDetailsResponse

import javax.inject.Singleton
import scala.collection.mutable
import scala.concurrent.Future

@Singleton
class InMemoryPurchaseImportRepository extends PurchaseImportRepository with Logging {

  private val data = mutable.Map.empty[(String, Long), Vector[StoredPurchaseDetails]]
  private val seededApplications = mutable.Set.empty[(String, Long)]

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
        val existingEntry = existing.find(_.purchaseDetails.itemNumber == assignedItemNumber)
        val itemType = existingEntry.map(_.purchaseImport.itemType).getOrElse("P")
        val importDetails = existingEntry.flatMap(_.importDetails)
        val storedEntry = StoredPurchaseDetails(applicationId, savedDetails, toPurchaseImport(savedDetails, itemType), sessionId, importDetails)

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

  override def findImportDetails(sessionId: String, applicationId: Long, itemNumber: Int): Future[Option[GetImportDetailsResponse]] =
    Future.successful {
      data.synchronized {
        data.getOrElse(sessionId -> applicationId, Vector.empty).find(_.purchaseDetails.itemNumber == itemNumber).flatMap(_.importDetails)
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
      if (!seededApplications.contains(key)) {
        val existing = data.getOrElse(key, Vector.empty)
        val seeds = defaultEntriesToSeed(applicationId, existing).map(_.copy(sessionKey = sessionId))
        if (seeds.nonEmpty) data.update(key, existing ++ seeds)
        seededApplications.add(key)
      }
    }
  }

  override def deleteImport(sessionId: String, applicationId: Long, itemNumber: Int): Future[Unit] = Future.successful {
    data.synchronized {
      val key = sessionId -> applicationId
      val existing = data.getOrElse(key, Vector.empty)
      data.update(
        key,
        existing.filterNot(entry => entry.purchaseImport.itemType == "I" && entry.purchaseImport.itemNumber == itemNumber)
      )
    }
  }

  override def deletePurchase(sessionId: String, applicationId: Long, itemNumber: Int): Future[Unit] = Future.successful {
    data.synchronized {
      val key = sessionId -> applicationId
      val existing = data.getOrElse(key, Vector.empty)
      data.update(
        key,
        existing.filterNot(entry => entry.purchaseImport.itemType == "P" && entry.purchaseImport.itemNumber == itemNumber)
      )
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
    case _ =>
      Vector(
        storedDefault(applicationId, 3, "P", goodsDescriptionCategory = "7", deductibleVatAmount = 111.11),
        storedDefault(applicationId, 2, "I", goodsDescriptionCategory = "10")
      )
  }

  private def defaultEntriesToSeed(applicationId: Long, existing: Vector[StoredPurchaseDetails]): Vector[StoredPurchaseDetails] = {
    val defaults = defaultEntries(applicationId)
    val existingItemNumbers = existing.map(_.purchaseDetails.itemNumber).toSet
    if (applicationId == 111 || applicationId == 999 || applicationId == 402)
      defaults.filterNot(entry => existingItemNumbers.contains(entry.purchaseDetails.itemNumber))
    else {
      val purchase =
        defaults.find(_.purchaseImport.itemType == "P").filterNot(entry => existingItemNumbers.contains(entry.purchaseDetails.itemNumber))
      val importSeed = defaults
        .find(_.purchaseImport.itemType == "I")
        .filter(_ => !existing.exists(_.purchaseImport.itemType == "I"))
        .map { entry =>
          val itemNumber = entry.purchaseDetails.itemNumber
          val occupiedItemNumbers = existingItemNumbers ++ purchase.map(_.purchaseDetails.itemNumber)
          val assignedItemNumber =
            if (occupiedItemNumbers.contains(itemNumber)) occupiedItemNumbers.maxOption.getOrElse(0) + 1 else itemNumber
          val details = entry.purchaseDetails.copy(itemNumber = assignedItemNumber)
          entry.copy(
            purchaseDetails = details,
            purchaseImport  = entry.purchaseImport.copy(itemNumber = assignedItemNumber),
            importDetails   = Some(defaultImportDetails(details.applicationId, details))
          )
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

    StoredPurchaseDetails(
      applicationId,
      purchaseDetails,
      toPurchaseImport(purchaseDetails, itemType),
      sessionKey    = "legacy",
      importDetails = Option.when(itemType == "I")(defaultImportDetails(applicationId, purchaseDetails))
    )
  }

  private def defaultImportDetails(applicationId: Long, purchaseDetails: UpdatePurchaseDetailsRequest): GetImportDetailsResponse = {
    val (supplierName, addressLine1, addressLine2, addressLine3, countryCode, issuingDate) = (applicationId, purchaseDetails.itemNumber) match {
      case (402, 4) =>
        (Some("Finnish International"),
         Some("356 High Street"),
         Some("Rochdale"),
         Some("England"),
         Some("GB"),
         java.time.LocalDateTime.of(2026, 5, 14, 0, 0)
        )
      case (402, 6) =>
        (Some("Baltic Importers"), Some("2 Harbour Road"), Some("Riga"), Some("Latvia"), Some("LV"), java.time.LocalDateTime.of(2026, 6, 2, 0, 0))
      case _ =>
        (Some("Import Supplier"), Some("1 Import Road"), None, None, Some("DE"), java.time.LocalDateTime.of(2026, 4, 1, 0, 0))
    }

    GetImportDetailsResponse(
      applicationId               = applicationId,
      itemNumber                  = purchaseDetails.itemNumber,
      issuingDate                 = Some(issuingDate),
      supplierName                = supplierName,
      supplierAddressLine1        = addressLine1,
      supplierAddressLine2        = addressLine2,
      supplierAddressLine3        = addressLine3,
      supplierCountryCode         = countryCode,
      goodsDescriptionCategory    = purchaseDetails.goodsDescriptionCategory,
      goodsDescriptionSubcategory = purchaseDetails.goodsDescriptionSubCategory,
      goodsDescriptionText        = Some(""),
      hasSadReferenceNumber       = Some("Y"),
      sadReferenceNumber          = Some(s"SAD$applicationId${purchaseDetails.itemNumber}"),
      referenceInformation        = Some("Import declaration reference"),
      currencyCode                = purchaseDetails.currencyCode,
      taxableAmount               = purchaseDetails.taxableAmount,
      vatAmount                   = purchaseDetails.vatAmount,
      deductibleVatAmount         = purchaseDetails.deductibleVatAmount,
      updateSequenceNumber        = purchaseDetails.updateSequenceNumber,
      periodStartDate             = java.time.LocalDateTime.of(2026, 1, 1, 0, 0)
    )
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
