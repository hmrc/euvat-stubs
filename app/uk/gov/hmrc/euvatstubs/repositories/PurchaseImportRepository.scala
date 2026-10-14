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
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.{Inject, Singleton}
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import scala.concurrent.{ExecutionContext, Future}

trait PurchaseImportRepository {
  def savePurchaseDetails(sessionId: String, applicationId: Long, purchaseDetails: UpdatePurchaseDetailsRequest): Future[UpdatePurchaseDetailsRequest]
  def findPurchaseDetails(sessionId: String, applicationId: Long, itemNumber: Int): Future[Option[UpdatePurchaseDetailsRequest]]
  def findPurchaseImports(sessionId: String, applicationId: Long): Future[List[PurchaseImport]]
  def seedDefaultPurchaseDetails(sessionId: String, applicationId: Long): Future[Unit]
  def deletePurchaseDetails(sessionId: String, applicationId: Long): Future[Unit]
}

case class StoredPurchaseDetails(
  applicationId: Long,
  purchaseDetails: UpdatePurchaseDetailsRequest,
  purchaseImport: PurchaseImport,
  sessionKey: String = "legacy"
)

object StoredPurchaseDetails {
  implicit val format: Format[StoredPurchaseDetails] = new Format[StoredPurchaseDetails] {
    override def writes(o: StoredPurchaseDetails): JsValue = Json.obj(
      "applicationId"   -> o.applicationId,
      "purchaseDetails" -> Json.toJson(o.purchaseDetails),
      "purchaseImport"  -> Json.toJson(o.purchaseImport),
      "sessionKey"      -> o.sessionKey
    )

    override def reads(json: JsValue): JsResult[StoredPurchaseDetails] =
      for {
        applicationId   <- (json \ "applicationId").validate[Long]
        purchaseDetails <- (json \ "purchaseDetails").validate[UpdatePurchaseDetailsRequest].orElse(fromPurchaseImport(applicationId, json))
        purchaseImport  <- (json \ "purchaseImport").validate[PurchaseImport].orElse(JsSuccess(toPurchaseImport(purchaseDetails, itemType = "P")))
        sessionKey      <- (json \ "sessionKey").validate[String].orElse(JsSuccess("legacy"))
      } yield StoredPurchaseDetails(applicationId, purchaseDetails, purchaseImport, sessionKey)

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
      val itemType = existing.find(_.purchaseDetails.itemNumber == assignedItemNumber).map(_.purchaseImport.itemType).getOrElse("P")
      val storedEntry = StoredPurchaseDetails(applicationId, savedDetails, toPurchaseImport(savedDetails, itemType), key)

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

  override def findPurchaseImports(sessionId: String, applicationId: Long): Future[List[PurchaseImport]] =
    collection
      .find(and(equal("sessionKey", sessionKey(sessionId)), equal("applicationId", applicationId)))
      .toFuture()
      .map(_.map(_.purchaseImport).toList)

  override def deletePurchaseDetails(sessionId: String, applicationId: Long): Future[Unit] =
    collection.deleteMany(and(equal("sessionKey", sessionKey(sessionId)), equal("applicationId", applicationId))).toFuture().map(_ => ())

  override def seedDefaultPurchaseDetails(sessionId: String, applicationId: Long): Future[Unit] = {
    val key = sessionKey(sessionId)
    collection.find(and(equal("sessionKey", key), equal("applicationId", applicationId))).toFuture().flatMap { existing =>
      val existingItemNumbers = existing.map(_.purchaseDetails.itemNumber).toSet
      val seeds = defaultEntries(applicationId)
        .filterNot(entry => existingItemNumbers.contains(entry.purchaseDetails.itemNumber))
        .map(_.copy(sessionKey = key))

      Future.sequence(seeds.map(collection.insertOne(_).toFuture())).map(_ => ())
    }
  }

  private def sessionKey(sessionId: String): String =
    java.util.Base64.getUrlEncoder.withoutPadding.encodeToString(
      MessageDigest.getInstance("SHA-256").digest(sessionId.getBytes(StandardCharsets.UTF_8))
    )

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
    case _ => List(storedDefault(applicationId, itemNumber = 3, itemType = "P", goodsDescriptionCategory = "7", deductibleVatAmount = 111.11))
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
