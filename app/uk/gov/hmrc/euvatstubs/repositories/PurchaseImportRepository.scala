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

import org.mongodb.scala.model.Filters.{and, equal}
import play.api.libs.json.{Format, JsResult, JsSuccess, JsValue, Json}
import uk.gov.hmrc.euvatstubs.models.PurchaseImport
import uk.gov.hmrc.euvatstubs.models.requests.UpdatePurchaseDetailsRequest
import uk.gov.hmrc.euvatstubs.models.responses.GetImportDetailsResponse
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import scala.concurrent.{ExecutionContext, Future}

trait PurchaseImportRepository {
  def savePurchaseDetails(sessionId: String, applicationId: Long, purchaseDetails: UpdatePurchaseDetailsRequest): Future[UpdatePurchaseDetailsRequest]
  def findPurchaseDetails(sessionId: String, applicationId: Long, itemNumber: Int): Future[Option[UpdatePurchaseDetailsRequest]]
  def findImportDetails(sessionId: String, applicationId: Long, itemNumber: Int): Future[Option[GetImportDetailsResponse]]
  def findPurchaseImports(sessionId: String, applicationId: Long): Future[List[PurchaseImport]]
  def seedDefaultPurchaseDetails(sessionId: String, applicationId: Long): Future[Unit]
  def deletePurchaseDetails(sessionId: String, applicationId: Long): Future[Unit]
  def deleteImport(sessionId: String, applicationId: Long, itemNumber: Int): Future[Unit]
}

case class StoredPurchaseDetails(
  applicationId: Long,
  purchaseDetails: UpdatePurchaseDetailsRequest,
  purchaseImport: PurchaseImport,
  sessionKey: String = "legacy",
  importDetails: Option[GetImportDetailsResponse] = None
)

object StoredPurchaseDetails {
  implicit val format: Format[StoredPurchaseDetails] = new Format[StoredPurchaseDetails] {
    override def writes(o: StoredPurchaseDetails): JsValue = Json.obj(
      "applicationId"   -> o.applicationId,
      "purchaseDetails" -> Json.toJson(o.purchaseDetails),
      "purchaseImport"  -> Json.toJson(o.purchaseImport),
      "sessionKey"      -> o.sessionKey,
      "importDetails"   -> Json.toJson(o.importDetails)
    )

    override def reads(json: JsValue): JsResult[StoredPurchaseDetails] =
      for {
        applicationId   <- (json \ "applicationId").validate[Long]
        purchaseDetails <- (json \ "purchaseDetails").validate[UpdatePurchaseDetailsRequest].orElse(fromPurchaseImport(applicationId, json))
        purchaseImport  <- (json \ "purchaseImport").validate[PurchaseImport].orElse(JsSuccess(toPurchaseImport(purchaseDetails, itemType = "P")))
        sessionKey      <- (json \ "sessionKey").validate[String].orElse(JsSuccess("legacy"))
        importDetails   <- (json \ "importDetails").validateOpt[GetImportDetailsResponse]
      } yield StoredPurchaseDetails(applicationId, purchaseDetails, purchaseImport, sessionKey, importDetails)

    private def fromPurchaseImport(applicationId: Long, json: JsValue): JsResult[UpdatePurchaseDetailsRequest] =
      (json \ "purchaseImport").validate[PurchaseImport].map { purchaseImport =>
        UpdatePurchaseDetailsRequest(
          applicationId               = applicationId,
          itemNumber                  = purchaseImport.itemNumber,
          goodsDescriptionCategory    = purchaseImport.goodsDescriptionCategory,
          goodsDescriptionSubCategory = purchaseImport.goodsDescriptionSubCategory,
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
          currencyCode                = Some(purchaseImport.currencyCode),
          taxableAmount               = Some(purchaseImport.taxableAmount),
          vatAmount                   = Some(purchaseImport.vatAmount),
          deductibleVatAmount         = Some(purchaseImport.deductibleVatAmount),
          updateSequenceNumber        = 1
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
}

private case class SeededPurchaseImportApplication(sessionKey: String, applicationId: Long, seedVersion: Int)

private object SeededPurchaseImportApplication {
  implicit val format: Format[SeededPurchaseImportApplication] = new Format[SeededPurchaseImportApplication] {
    override def writes(value: SeededPurchaseImportApplication): JsValue = Json.obj(
      "sessionKey"    -> value.sessionKey,
      "applicationId" -> value.applicationId,
      "seedVersion"   -> value.seedVersion
    )

    override def reads(json: JsValue): JsResult[SeededPurchaseImportApplication] =
      for {
        sessionKey    <- (json \ "sessionKey").validate[String].orElse(JsSuccess("legacy"))
        applicationId <- (json \ "applicationId").validate[Long]
        seedVersion   <- (json \ "seedVersion").validateOpt[Int]
      } yield SeededPurchaseImportApplication(sessionKey, applicationId, seedVersion.getOrElse(1))
  }
}

@Singleton
class MongoPurchaseImportRepository @Inject() (
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[StoredPurchaseDetails](
      mongoComponent = mongoComponent,
      collectionName = "purchaseImports",
      domainFormat   = StoredPurchaseDetails.format,
      indexes        = Seq.empty
    )
    with PurchaseImportRepository {

  private val seededApplications = new PlayMongoRepository[SeededPurchaseImportApplication](
    mongoComponent = mongoComponent,
    collectionName = "purchaseImportSeeds",
    domainFormat   = SeededPurchaseImportApplication.format,
    indexes        = Seq.empty
  )

  override def savePurchaseDetails(sessionId: String,
                                   applicationId: Long,
                                   purchaseDetails: UpdatePurchaseDetailsRequest
                                  ): Future[UpdatePurchaseDetailsRequest] = {
    val key = sessionKey(sessionId)
    val applicationFilter = and(equal("sessionKey", key), equal("applicationId", applicationId))
    collection.find(applicationFilter).toFuture().flatMap { existing =>

      val assignedItemNumber =
        if (purchaseDetails.itemNumber > 0 && existing.exists(_.purchaseDetails.itemNumber == purchaseDetails.itemNumber)) purchaseDetails.itemNumber
        else if (purchaseDetails.itemNumber > 0) purchaseDetails.itemNumber
        else existing.map(_.purchaseDetails.itemNumber).foldLeft(0)(Math.max) + 1

      val savedDetails = purchaseDetails.copy(applicationId = applicationId, itemNumber = assignedItemNumber)
      val existingEntry = existing.find(_.purchaseDetails.itemNumber == assignedItemNumber)
      val itemType = existingEntry.map(_.purchaseImport.itemType).getOrElse("P")
      val importDetails = existingEntry.flatMap(_.importDetails)
      val storedEntry = StoredPurchaseDetails(applicationId, savedDetails, toPurchaseImport(savedDetails, itemType), key, importDetails)

      val filter = and(applicationFilter, equal("purchaseDetails.itemNumber", assignedItemNumber))
      val replaceExisting = existing.exists(_.purchaseDetails.itemNumber == assignedItemNumber)

      if (replaceExisting) collection.replaceOne(filter, storedEntry).toFuture().map(_ => savedDetails)
      else collection.insertOne(storedEntry).toFuture().map(_ => savedDetails)
    }
  }

  override def findPurchaseDetails(sessionId: String, applicationId: Long, itemNumber: Int): Future[Option[UpdatePurchaseDetailsRequest]] =
    collection
      .find(and(equal("sessionKey", sessionKey(sessionId)), equal("applicationId", applicationId)))
      .toFuture()
      .map(_.find(_.purchaseDetails.itemNumber == itemNumber).map(_.purchaseDetails))

  override def findImportDetails(sessionId: String, applicationId: Long, itemNumber: Int): Future[Option[GetImportDetailsResponse]] =
    collection
      .find(and(equal("sessionKey", sessionKey(sessionId)), equal("applicationId", applicationId)))
      .toFuture()
      .map(_.find(_.purchaseDetails.itemNumber == itemNumber).flatMap(_.importDetails))

  override def findPurchaseImports(sessionId: String, applicationId: Long): Future[List[PurchaseImport]] =
    collection
      .find(and(equal("sessionKey", sessionKey(sessionId)), equal("applicationId", applicationId)))
      .toFuture()
      .map(_.map(_.purchaseImport).toList)

  override def deletePurchaseDetails(sessionId: String, applicationId: Long): Future[Unit] = {
    val filter = and(equal("sessionKey", sessionKey(sessionId)), equal("applicationId", applicationId))
    collection.deleteMany(filter).toFuture().flatMap { _ =>
      seededApplications.collection.deleteMany(filter).toFuture().map(_ => ())
    }
  }

  override def seedDefaultPurchaseDetails(sessionId: String, applicationId: Long): Future[Unit] = {
    val key = sessionKey(sessionId)
    val seedFilter = and(equal("sessionKey", key), equal("applicationId", applicationId))
    seededApplications.collection.find(seedFilter).toFuture().flatMap {
      case existingSeeds if existingSeeds.headOption.exists(_.seedVersion >= 3) => seedMissingImportDetails(key, applicationId)
      case existingSeeds if existingSeeds.nonEmpty =>
        seedLegacyDefaultImport(key, applicationId).flatMap { _ =>
          seededApplications.collection
            .replaceOne(seedFilter, SeededPurchaseImportApplication(key, applicationId, seedVersion = 3))
            .toFuture()
            .flatMap(_ => seedMissingImportDetails(key, applicationId))
        }
      case _ =>
        collection.find(seedFilter).toFuture().flatMap { existing =>
          val seeds = defaultEntriesToSeed(applicationId, existing).map(_.copy(sessionKey = key))

          Future.sequence(seeds.map(collection.insertOne(_).toFuture())).flatMap { _ =>
            seededApplications.collection
              .insertOne(SeededPurchaseImportApplication(key, applicationId, seedVersion = 3))
              .toFuture()
              .flatMap(_ => seedMissingImportDetails(key, applicationId))
          }
        }
    }
  }

  private def sessionKey(sessionId: String): String =
    java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(
      MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8))
    )

  private def seedLegacyDefaultImport(key: String, applicationId: Long): Future[Unit] =
    if (applicationId == 111 || applicationId == 999 || applicationId == 402) Future.unit
    else {
      collection.find(and(equal("sessionKey", key), equal("applicationId", applicationId))).toFuture().flatMap { existing =>
        val importSeed = defaultEntries(applicationId).find(_.purchaseImport.itemType == "I")
        val alreadyHasImport = existing.exists(_.purchaseImport.itemType == "I")
        importSeed.filter(_ => !alreadyHasImport).fold(Future.unit) { seed =>
          collection.insertOne(withAvailableImportItemNumber(seed, existing).copy(sessionKey = key)).toFuture().map(_ => ())
        }
      }
    }

  private def defaultEntriesToSeed(applicationId: Long, existing: Seq[StoredPurchaseDetails]): List[StoredPurchaseDetails] = {
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
        .map(entry => withAvailableImportItemNumber(entry, existing ++ purchase.toSeq))
      purchase.toList ++ importSeed.toList
    }
  }

  private def withAvailableImportItemNumber(
    entry: StoredPurchaseDetails,
    existing: Seq[StoredPurchaseDetails]
  ): StoredPurchaseDetails = {
    val itemNumber = entry.purchaseDetails.itemNumber
    val assignedItemNumber =
      if (existing.exists(_.purchaseDetails.itemNumber == itemNumber)) existing.map(_.purchaseDetails.itemNumber).maxOption.getOrElse(0) + 1
      else itemNumber
    val details = entry.purchaseDetails.copy(itemNumber = assignedItemNumber)
    entry.copy(
      purchaseDetails = details,
      purchaseImport  = entry.purchaseImport.copy(itemNumber = assignedItemNumber),
      importDetails   = Some(defaultImportDetails(entry.applicationId, details))
    )
  }

  private def seedMissingImportDetails(key: String, applicationId: Long): Future[Unit] =
    collection.find(and(equal("sessionKey", key), equal("applicationId", applicationId))).toFuture().flatMap { existing =>
      val updates = existing.filter(entry => entry.purchaseImport.itemType == "I" && entry.importDetails.isEmpty).map { entry =>
        val updatedEntry = entry.copy(importDetails = Some(defaultImportDetails(entry)))
        val filter = and(
          equal("sessionKey", key),
          equal("applicationId", applicationId),
          equal("purchaseDetails.itemNumber", entry.purchaseDetails.itemNumber)
        )
        collection.replaceOne(filter, updatedEntry).toFuture()
      }
      Future.sequence(updates).map(_ => ())
    }

  override def deleteImport(sessionId: String, applicationId: Long, itemNumber: Int): Future[Unit] =
    collection
      .deleteOne(
        and(
          equal("sessionKey", sessionKey(sessionId)),
          equal("applicationId", applicationId),
          equal("purchaseDetails.itemNumber", itemNumber),
          equal("purchaseImport.itemType", "I")
        )
      )
      .toFuture()
      .map(_ => ())

  private def defaultEntries(applicationId: Long): List[StoredPurchaseDetails] = applicationId match {
    case 111 => List(storedDefault(applicationId, itemNumber = 1, itemType = "P"))
    case 999 => List(storedDefault(applicationId, itemNumber = 2, itemType = "I"))
    case 402 =>
      List(
        storedDefault(applicationId, itemNumber = 4, itemType = "I", goodsDescriptionCategory = "3", deductibleVatAmount  = 0),
        storedDefault(applicationId, itemNumber = 1, itemType = "P"),
        storedDefault(applicationId, itemNumber = 5, itemType = "P", goodsDescriptionCategory = "7", deductibleVatAmount  = 0),
        storedDefault(applicationId, itemNumber = 2, itemType = "P", goodsDescriptionCategory = "9", deductibleVatAmount  = 123.45),
        storedDefault(applicationId, itemNumber = 6, itemType = "I", goodsDescriptionCategory = "10", deductibleVatAmount = 0),
        storedDefault(applicationId, itemNumber = 3, itemType = "P", goodsDescriptionCategory = "9", deductibleVatAmount  = 999)
      )
    case _ =>
      List(
        storedDefault(applicationId, itemNumber = 3, itemType = "P", goodsDescriptionCategory = "7", deductibleVatAmount = 111.11),
        storedDefault(applicationId, itemNumber = 2, itemType = "I", goodsDescriptionCategory = "10")
      )
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

  private def defaultImportDetails(storedEntry: StoredPurchaseDetails): GetImportDetailsResponse =
    defaultImportDetails(storedEntry.applicationId, storedEntry.purchaseDetails)

  private def defaultImportDetails(applicationId: Long, purchaseDetails: UpdatePurchaseDetailsRequest): GetImportDetailsResponse = {
    val (supplierName, addressLine1, addressLine2, addressLine3, countryCode, issuingDate) = (applicationId, purchaseDetails.itemNumber) match {
      case (402, 4) =>
        (Some("Finnish International"), Some("356 High Street"), Some("Rochdale"), Some("England"), Some("GB"), LocalDateTime.of(2026, 5, 14, 0, 0))
      case (402, 6) =>
        (Some("Baltic Importers"), Some("2 Harbour Road"), Some("Riga"), Some("Latvia"), Some("LV"), LocalDateTime.of(2026, 6, 2, 0, 0))
      case _ =>
        (Some("Import Supplier"), Some("1 Import Road"), None, None, Some("DE"), LocalDateTime.of(2026, 4, 1, 0, 0))
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
      periodStartDate             = LocalDateTime.of(2026, 1, 1, 0, 0)
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
