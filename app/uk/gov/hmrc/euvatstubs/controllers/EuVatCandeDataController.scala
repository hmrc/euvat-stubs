/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.euvatstubs.models.requests.*
import uk.gov.hmrc.euvatstubs.models.responses.*
import uk.gov.hmrc.euvatstubs.models.{Attachment, LatestApplication, PurchaseImport}
import uk.gov.hmrc.euvatstubs.repositories.VrnStateRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.collection.concurrent.TrieMap
import java.util.concurrent.atomic.AtomicInteger
import scala.util.control.NonFatal

@Singleton
class EuVatCandeDataController @Inject() (cc: ControllerComponents, vrnStateRepository: VrnStateRepository)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging:

  private val nextImportItemNumbers = TrieMap.empty[Long, AtomicInteger]

  def addApplication(vrn: String): Action[AnyContent] = Action { implicit request =>
    logger.info(s"Stub: Creating refund application for vrn: $vrn")

    val response = if (vrn.endsWith("111")) { ApplicationResponse(111, "GB123111", 1) }
    else if (vrn.endsWith("666")) { ApplicationResponse(666, "GB123666", 3) }
    else if (vrn.endsWith("999")) { ApplicationResponse(999, "GB123999", 2) }
    else if (vrn.endsWith("880")) { ApplicationResponse(402, "GB123406", 4) }
    else { ApplicationResponse(660, "GB123100", 6) }

    Ok(Json.toJson(response))
  }

  private def latestApplicationResponse(): LatestApplicationResponse = LatestApplicationResponse(
    applications = List(
      LatestApplication(
        applicationId        = 404,
        refundingCountryCode = "LV",
        periodStartDate      = LocalDateTime.of(2026, 2, 1, 0, 0),
        periodEndDate        = LocalDateTime.of(2026, 5, 31, 23, 59),
        applicationNumber    = "GB0000000000000404",
        applicationStatus    = Some("D"),
        submissionStatus     = Some("S"),
        applicationVersion   = LocalDateTime.of(2026, 4, 22, 0, 0, 0, 0)
      )
    ),
    totalApplication = 1
  )

  def getLatestApplications: Action[AnyContent] = Action.async { implicit request =>
    logger.info("Stub: returning latest Applications")
    val body = request.body.asJson

    def mapVrnToSim(v: String): String = v match {
      case "111111115"               => "SIM-5XX"
      case "999900003"               => "SIM-DUP" // Duplicate found, returns record with one or more total applications
      case "222222222" | "333333333" => "SIM-OK" // No duplicate, returns no record with zero total application
      case "444444444"               => "SIM-4XX"
      case "555555555"               => "SIM-STATE"
      case other                     => other
    }

    val responseOpt = body.flatMap { json =>
      val applicantOpt: Option[String] = (json \ "applicantVatRegNumber")
        .asOpt[String]
        .orElse((json \ "applicantVatRegNumber").asOpt[Long].map(_.toString))
      applicantOpt.map { applicant =>
        val sim = mapVrnToSim(applicant)
        logger.info(s"Stub: applicantVatRegNumber=$applicant mappedTo=$sim")

        sim match {
          case "SIM-5XX"   => Future.successful(Left(InternalServerError("simulated 5xx")))
          case "SIM-4XX"   => Future.successful(Left(BadRequest("simulated 4xx")))
          case "SIM-OK"    => Future.successful(Right(LatestApplicationResponse(Nil, 0)))
          case "SIM-STATE" =>
            // Use persistent counter: increment then decide based on previous value
            vrnStateRepository.incrementAndGet(applicant).map { newCount =>
              val oldCount = newCount - 1
              val idx = oldCount % 3
              if (idx < 2) {
                Right(LatestApplicationResponse(Nil, 0))
              } else {
                val app = LatestApplication(
                  applicationId        = 1L,
                  refundingCountryCode = (json \ "refundingCountry").asOpt[String].getOrElse("LV"),
                  periodStartDate      = LocalDateTime.of(2024, 1, 1, 0, 0),
                  periodEndDate        = LocalDateTime.of(2024, 12, 31, 23, 59),
                  applicationNumber    = f"GB-DUP-STATE-$newCount%04d",
                  applicationStatus    = Some("D"),
                  submissionStatus     = None,
                  applicationVersion   = LocalDateTime.now()
                )
                Right(LatestApplicationResponse(List(app), 1))
              }
            }
          case "SIM-DUP" =>
            val app = LatestApplication(
              applicationId        = 1L,
              refundingCountryCode = (json \ "refundingCountry").asOpt[String].getOrElse("LV"),
              periodStartDate      = LocalDateTime.of(2026, 1, 1, 0, 0),
              periodEndDate        = LocalDateTime.of(2026, 12, 31, 23, 59),
              applicationNumber    = "GB-DUP-0001",
              applicationStatus    = Some("D"),
              submissionStatus     = None,
              applicationVersion   = LocalDateTime.now()
            )
            Future.successful(Right(LatestApplicationResponse(List(app), 1)))
          case _ =>
            val countryOpt = (json \ "refundingCountry").asOpt[String]
            val startDateOpt = (json \ "startDate").asOpt[String]
            val endDateOpt = (json \ "endDate").asOpt[String]
            val baseApps = latestApplicationResponse().applications

            val filtered = (countryOpt, startDateOpt, endDateOpt) match {
              case (Some(country), Some(sd), Some(ed)) if sd.nonEmpty && ed.nonEmpty =>
                try {
                  val reqStart = LocalDateTime.parse(sd.take(19))
                  val reqEnd = LocalDateTime.parse(ed.take(19))
                  baseApps.filter { app =>
                    app.refundingCountryCode == country &&
                    !app.periodStartDate.isAfter(reqEnd) &&
                    !app.periodEndDate.isBefore(reqStart)
                  }
                } catch {
                  case NonFatal(_) => baseApps
                }
              case (Some(country), _, _) => baseApps.filter(_.refundingCountryCode == country)
              case _                     => baseApps
            }
            Future.successful(Right(LatestApplicationResponse(filtered, 0)))
        }
      }
    }

    responseOpt match {
      case Some(futureEither) =>
        futureEither.flatMap {
          case Left(err: Result) => Future.successful(err)
          case Right(r)          => Future.successful(Ok(Json.toJson(r)))
        }
      case None => Future.successful(BadRequest("applicantVatRegNumber is missing or empty"))
    }
  }

  def addPurchase(): Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: adding purchase")

    request.body.asJson.flatMap(_.asOpt[AddPurchaseRequest]) match {
      case None    => BadRequest("Invalid or missing request body")
      case Some(_) => Ok(Json.toJson(AddPurchaseResponse(itemNumber = 4, updateSequenceNumber = 1)))
    }
  }

  def addImport: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: adding import (rds-cande-proxy)")

    request.body.asJson.flatMap(_.asOpt[AddImportRequest]) match {
      case None => BadRequest("Invalid or missing request body")
      case Some(req) =>
        if (req.applicationId % 1000 == 500) InternalServerError("Simulated database connectivity failure")
        else {
          val itemNumber = nextImportItemNumbers.getOrElseUpdate(req.applicationId, new AtomicInteger(3)).incrementAndGet()
          Ok(Json.toJson(AddPurchaseResponse(itemNumber = itemNumber, updateSequenceNumber = req.updateSequenceNumber)))
        }
    }
  }

  private val purchaseDetailsResponse: GetPurchaseDetailsResponse = GetPurchaseDetailsResponse(
    goodsDescriptionCode       = "7",
    goodsDescriptionSubCode    = Some("7.1.1"),
    goodsDescriptionText       = None,
    simplifiedInvoiceIndicator = Some("N"),
    supplierName               = Some("Test Supplier Name"),
    supplierAddressLine1       = Some("Test address one"),
    supplierAddressLine2       = Some("Test address two"),
    supplierAddressLine3       = Some("Test address three"),
    supplierVatNumber          = Some("EE0000000111"),
    supplierTaxIdentifier      = None,
    invoiceDate                = Some(LocalDateTime.of(2025, 12, 8, 0, 0)),
    invoiceNumber              = Some("1234567890"),
    currencyCode               = Some("EUR"),
    taxableAmount              = Some(BigDecimal("1000.01")),
    vatAmount                  = Some(BigDecimal("200.01")),
    deductibleVatAmount        = Some(BigDecimal("100.01")),
    updateSequenceNumber       = 1
  )

  def getPurchaseDetails: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning purchase details")

    request.body.asJson.flatMap(_.asOpt[GetPurchaseDetailsRequest]) match {
      case Some(_) => Ok(Json.toJson(purchaseDetailsResponse))
      case None    => BadRequest("Invalid or missing request body")
    }
  }

  private val importDetailsResponse: GetImportDetailsResponse = GetImportDetailsResponse(
    applicationId           = 404,
    itemNumber              = 4,
    issuingDate             = Some(LocalDateTime.of(2026, 1, 1, 0, 0)),
    supplierName            = Some("Test Supplier"),
    supplierAddressLine1    = Some("Test address one"),
    supplierAddressLine2    = Some("Test address two"),
    supplierAddressLine3    = Some("Test address three"),
    supplierCountryCode     = Some("HM"),
    goodsDescriptionCode    = "7",
    goodsDescriptionSubCode = Some("7.1.1"),
    goodsDescriptionText    = None,
    hasSadReferenceNumber   = Some("Y"),
    sadReferenceNumber      = Some("SAD123456789"),
    referenceInformation    = Some("Import declaration reference"),
    currencyCode            = Some("EUR"),
    taxableAmount           = Some(BigDecimal("1000.01")),
    vatAmount               = Some(BigDecimal("2000.01")),
    deductibleVatAmount     = Some(BigDecimal("2500.01")),
    updateSequenceNumber    = 1,
    periodStartDate         = LocalDateTime.of(2026, 1, 1, 0, 0)
  )

  def getImportDetails(applicationId: Long, itemNumber: Int): Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning import details")

    // Simulate backend/database connectivity failure when applicationId ends with 500
    if (applicationId % 1000 == 500) InternalServerError("Simulated database connectivity failure")
    else if (applicationId == 0) BadRequest(s"No import found for the requested applicationId: $applicationId")
    else Ok(Json.toJson(importDetailsResponse.copy(applicationId = applicationId, itemNumber = itemNumber)))
  }

  def updateImportDetails: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: updateImportDetails called")

    request.body.asJson.flatMap(_.asOpt[UpdateImportDetailsRequest]) match {
      case None => BadRequest("Invalid or missing request body")
      // Simulate backend/database connectivity failure when applicationId ends with 500
      case Some(req) if req.applicationId % 1000 == 500 => InternalServerError("Simulated database connectivity failure")
      case Some(req) if req.applicationId <= 0 || req.itemNumber <= 0 =>
        BadRequest(s"Invalid applicationId or itemNumber: applicationId=${req.applicationId} itemNumber=${req.itemNumber}")
      case Some(req) =>
        // mimic the proxy: the update stored procedure bumps the sequence number by 1
        val newSeq = req.updateSequenceNumber + 1
        logger.info(s"Stub updateImportDetails: applicationId=${req.applicationId}, itemNumber=${req.itemNumber}, newSeq=$newSeq")
        Ok(Json.toJson(UpdateImportDetailsResponse(newSeq)))
    }
  }

  def getSupplierTaxIdentifierCount: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning SupplierTaxIdentifierCount")

    request.body.asJson.flatMap(_.validate[SupplierTaxIdentifierCountRequest].asOpt) match {
      case None                                           => BadRequest("Invalid or missing request body")
      case Some(req) if req.taxIdentifier.endsWith("500") => InternalServerError("Simulated database connectivity failure")
      case Some(req) =>
        val count = (req.invoiceNumber, req.taxIdentifier) match {
          case ("INV-1", "TID-1") => 1 // triggers duplicate warning
          case ("INV-1", "TID-2") => 2 // triggers duplicate warning
          case _                  => 0 // no warning triggers
        }

        logger.info(s"Stub getSupplierTaxIdentifierCount: taxIdentifier=${req.taxIdentifier}, invoiceNumber=${req.invoiceNumber}, count=$count")
        Ok(Json.toJson(SupplierTaxIdentifierCountResponse(count)))
    }
  }

  def getSupplierVrnCount: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning supplier VRN count")

    request.body.asJson.flatMap(_.validate[SupplierVrnCountRequest].asOpt) match {
      case None                                       => BadRequest("Invalid or missing request body")
      case Some(req) if req.vatNumber.endsWith("500") => InternalServerError("Simulated database connectivity failure")
      case Some(req)                                  =>
        // Duplicate only when the VRN suffix triggers AND the invoice number is the "known duplicate" value.
        // Changing either the VRN (to a non-111/222 suffix) or the invoice (away from DUP) clears the warning.
        val count = (req.invoiceNumber, req.vatNumber.takeRight(3)) match {
          case ("DUP", "111") => 1
          case ("DUP", "222") => 2
          case _              => 0
        }

        logger.info(s"Stub getSupplierVrnCount: vatNumber=${req.vatNumber}, invoiceNumber=${req.invoiceNumber}, count=$count")
        Ok(Json.toJson(SupplierVrnCountResponse(count)))
    }
  }

  def updatePurchaseDetails(): Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: updatePurchaseDetails called")
    val bodyOpt = request.body.asJson

    bodyOpt match {
      case None       => BadRequest("Invalid or missing request body")
      case Some(json) =>
        // invoice-only simulated errors
        (json \ "invoiceNumber").asOpt[String] match {
          case Some("INV-UP-500") => InternalServerError("simulated 5xx")
          case Some("INV-UP-400") => BadRequest("simulated 4xx")
          case _ =>
            json.asOpt[UpdatePurchaseDetailsRequest] match {
              case Some(req) => Ok(Json.obj("updateSequenceNumber" -> req.updateSequenceNumber))
              case None =>
                val applicationIdOpt: Option[Long] = (json \ "applicationId").asOpt[Long]
                val goodsDescriptionCategoryOpt: Option[String] = (json \ "goodsDescriptionCode").asOpt[String]
                val goodsDescriptionTextOpt: Option[String] = (json \ "goodsDescriptionText").asOpt[String]
                val purchaseSubcategoryOpt: Option[String] = (json \ "goodsDescriptionSubCode")
                  .asOpt[String]
                  .orElse((json \ "goodsDescriptionSubCode").asOpt[String])
                val simplifiedInvoiceIndicatorOpt = (json \ "simplifiedInvoiceIndicator").asOpt[String]
                val supplierNameOpt = (json \ "supplierName").asOpt[String]
                val supplierAddress1Opt = (json \ "supplierAddressLine1")
                  .asOpt[String]
                  .orElse((json \ "supplierAddress1").asOpt[String])
                val supplierAddress2Opt = (json \ "supplierAddressLine2")
                  .asOpt[String]
                  .orElse((json \ "supplierAddress2").asOpt[String])
                val supplierAddress3Opt = (json \ "supplierAddressLine3")
                  .asOpt[String]
                  .orElse((json \ "supplierAddress3").asOpt[String])
                val supplierVatRegNumberOpt = (json \ "supplierVatNumber")
                  .asOpt[String]
                  .orElse((json \ "supplierVatRegNumber").asOpt[String])
                val supplierTaxIdentifierOpt = (json \ "supplierTaxIdentifier").asOpt[String]
                val invoiceDateOpt = (json \ "invoiceDate").asOpt[String].flatMap { s =>
                  try Some(LocalDateTime.parse(s.take(19)))
                  catch { case NonFatal(_) => None }
                }
                val invoiceNumberOpt = (json \ "invoiceNumber").asOpt[String]
                val currencyCodeOpt = (json \ "currencyCode").asOpt[String]
                val taxableAmountOpt = (json \ "taxableAmount").asOpt[BigDecimal]
                val vatAmountOpt = (json \ "vatAmount").asOpt[BigDecimal]
                val deductibleVatAmountOpt = (json \ "deductibleVatAmount").asOpt[BigDecimal]
                val updateSequenceNumberOpt = (json \ "updateSequenceNumber").asOpt[Int]
                // itemNumber is not used by the stub; read if present
                val itemNumberOpt = (json \ "itemNumber").asOpt[Int]

                (applicationIdOpt, goodsDescriptionCategoryOpt, updateSequenceNumberOpt) match {
                  case (Some(applicationId), Some(goodsDescriptionCategory), Some(updateSeq)) =>
                    val req = UpdatePurchaseDetailsRequest(
                      applicationId              = applicationId,
                      itemNumber                 = itemNumberOpt.getOrElse(0),
                      goodsDescriptionCode       = goodsDescriptionCategory,
                      goodsDescriptionSubCode    = purchaseSubcategoryOpt,
                      goodsDescriptionText       = goodsDescriptionTextOpt,
                      simplifiedInvoiceIndicator = simplifiedInvoiceIndicatorOpt,
                      supplierName               = supplierNameOpt,
                      supplierAddress1           = supplierAddress1Opt,
                      supplierAddress2           = supplierAddress2Opt,
                      supplierAddress3           = supplierAddress3Opt,
                      supplierVatRegNumber       = supplierVatRegNumberOpt,
                      supplierTaxIdentifier      = supplierTaxIdentifierOpt,
                      invoiceDate                = invoiceDateOpt,
                      invoiceNumber              = invoiceNumberOpt,
                      currencyCode               = currencyCodeOpt,
                      taxableAmount              = taxableAmountOpt,
                      vatAmount                  = vatAmountOpt,
                      deductibleVatAmount        = deductibleVatAmountOpt,
                      updateSequenceNumber       = updateSeq
                    )

                    Ok(Json.obj("updateSequenceNumber" -> req.updateSequenceNumber))

                  case _ => BadRequest("Invalid or missing request body")
                }
            }
        }
    }
  }

  private val purchaseImport: PurchaseImport = PurchaseImport(
    itemNumber              = 1,
    itemType                = "P",
    goodsDescriptionCode    = "1",
    goodsDescriptionSubCode = Some("1.2"),
    currencyCode            = "DE",
    taxableAmount           = 333,
    vatAmount               = 222,
    deductibleVatAmount     = 111.11
  )

  def getPurchaseImportList: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning purchase and import list")

    request.body.asJson.flatMap(_.validate[PurchaseImportListRequest].asOpt).fold(BadRequest("Invalid or missing request body")) { request =>
      request.applicationId match {
        case 0 => BadRequest(s"No items found for the requested applicationId: ${request.applicationId}")
        case _ =>
          val response: List[PurchaseImport] =
            request.applicationId match {
              case 111 =>
                List(
                  purchaseImport,
                  purchaseImport.copy(itemType = "I", itemNumber = 2, deductibleVatAmount = 0)
                )
              case 999 => List(purchaseImport.copy(itemType = "I", itemNumber = 2, deductibleVatAmount = 0))
              case 402 =>
                List(
                  purchaseImport.copy(goodsDescriptionCode = "3", itemType = "I", itemNumber = 4, deductibleVatAmount = 0),
                  purchaseImport,
                  purchaseImport.copy(goodsDescriptionCode = "7", itemType   = "P", itemNumber        = 5, deductibleVatAmount = 0),
                  purchaseImport.copy(goodsDescriptionCode = "9", itemNumber = 2, deductibleVatAmount = 123.45),
                  purchaseImport.copy(goodsDescriptionCode = "10", itemType  = "I", itemNumber        = 6, deductibleVatAmount = 0),
                  purchaseImport.copy(goodsDescriptionCode = "9", itemNumber = 3, deductibleVatAmount = 999)
                )
              case _ =>
                List(
                  purchaseImport.copy(goodsDescriptionCode = "7", itemType = "P", itemNumber = 3, deductibleVatAmount = 111.11),
                  purchaseImport.copy(goodsDescriptionCode = "7", itemType = "I", itemNumber = 4, deductibleVatAmount = 10)
                )
            }

          Ok(Json.toJson(PurchaseImportListResponse(totalItems = response.size, purchaseImportList = response, totalVatClaims = BigDecimal(456.78))))
      }
    }
  }

  def deleteApplication: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: deleteApplication called")
    val bodyOpt = request.body.asJson

    bodyOpt match {
      case None => BadRequest("Invalid or missing request body")
      case Some(json) =>
        json.asOpt[DeleteApplicationRequest] match {
          case None      => BadRequest("Invalid or missing request body")
          case Some(req) =>
            // Simulate a backend/database failure when applicationId ends with 500
            if (req.applicationId % 1000 == 500) InternalServerError("Simulated database connectivity failure")
            else Ok
        }
    }
  }

  def updateApplicationDetails: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: updateApplicationDetails called")

    request.body.asJson.flatMap(_.asOpt[UpdateApplicationDetailsRequest]) match {
      case None                                                          => BadRequest("Invalid or missing request body")
      case Some(req) if req.applicantEmailAddress == "error500@test.com" => InternalServerError("simulated 5xx")
      case Some(req) if req.applicantEmailAddress == "error400@test.com" => BadRequest("simulated 4xx")
      case Some(req)                                                     =>
        // mimic the proxy: language SP then details SP, each bumps seq by 1
        val newSeq = req.updateSequenceNumber + 2
        logger.info(s"Stub updateApplicationDetails: applicationId=${req.applicationId}, newSeq=$newSeq")
        Ok(Json.toJson(UpdateApplicationDetailsResponse(newSeq)))
    }
  }

  def deletePurchase: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: deletePurchase called")

    request.body.asJson.flatMap(_.asOpt[DeletePurchaseRequest]) match {
      case None                               => BadRequest("Invalid or missing request body")
      case Some(req) if req.itemNumber == 500 => InternalServerError("Simulated database connectivity failure")
      case Some(req)                          =>
        // mimic the proxy: deletePurchaseDetails bumps seq by 1
        val newSeq = req.updateSequenceNumber + 1
        logger.info(s"Stub deletePurchase: applicationId=${req.applicationId}, itemNumber=${req.itemNumber}, newSeq=$newSeq")
        Ok(Json.toJson(DeletePurchaseResponse(newSeq)))
    }
  }

  def getSadReferenceCount: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning SAD reference count")

    request.body.asJson.flatMap(_.validate[SadReferenceCountRequest].asOpt) match {
      case None                                                => BadRequest("Invalid or missing request body")
      case Some(req) if req.sadReferenceNumber.endsWith("500") => InternalServerError("Simulated database connectivity failure")
      case Some(req) =>
        val count = req.sadReferenceNumber.takeRight(3) match {
          case "111" => 1
          case "222" => 2
          case _     => 0
        }

        logger.info(s"Stub getSadReferenceCount: sadReferenceNumber=${req.sadReferenceNumber},count=$count")
        Ok(Json.toJson(SadReferenceCountResponse(count)))
    }
  }

  def deleteImport(): Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: deleteImport called")

    request.body.asJson.flatMap(_.asOpt[DeleteImportRequest]) match {
      case None => BadRequest("Invalid or missing request body")
      case Some(req) =>
        val newSeq = req.updateSequenceNumber
        logger.info(s"Stub deleteImport: applicationId=${req.applicationId}, itemNumber=${req.itemNumber}, newSeq=$newSeq")
        Ok(Json.obj("updateSequenceNumber" -> newSeq))
    }
  }

  def getReferenceInformationCount: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: getReferenceInformationCount called")

    request.body.asJson.flatMap(_.validate[ReferenceInfoCountRequest].asOpt) match {
      case None => BadRequest("Invalid or missing request body")
      case Some(req) =>
        val count = math.max(req.itemNumber - 4, 0)

        logger.info(s"Stub getReferenceInformationCount: applicationId=${req.applicationId}, itemNumber=${req.itemNumber}, count=$count")
        Ok(Json.toJson(ReferenceInfoCountResponse(count)))
    }
  }

  private def applicationDetails(applicationId: Long): ApplicationDetails =
    ApplicationDetails(
      applicationId              = applicationId,
      applicationNumber          = Some("GB123456789012345"),
      refundingCountryCode       = Some("LV"),
      applicationVersion         = Some(LocalDateTime.of(2009, 2, 1, 0, 0, 0, 0)),
      periodStartDate            = Some(LocalDateTime.of(2011, 6, 1, 0, 0, 0, 0)),
      periodEndDate              = Some(LocalDateTime.of(2011, 10, 31, 23, 59, 59, 999000000)),
      applicantVatRegNumber      = Some("GB123456789"),
      applicantEmailAddress      = Some("applicant@example.com"),
      applicantTelephoneNumber   = Some("01952233248"),
      representativeId           = Some("REP123456"),
      representativeIdType       = Some("VAT"),
      representativeCountry      = Some("GB"),
      representativeEmailAddress = Some("representative@example.com"),
      representativeTelephoneNum = Some("01952233249"),
      applicationLanguage        = Some("en"),
      bankAccountOwnerName       = Some("Example Applicant"),
      bankAccountOwnerType       = Some("B"),
      ibanCode                   = Some("GB29NWBK60161331926819"),
      bicCode                    = Some("NWBKGB2L"),
      bankAccountCurrencyCode    = Some("EUR"),
      businessActivityCode1      = Some("2345"),
      businessActivityCode2      = Some("6789"),
      businessActivityCode3      = Some("1234"),
      messageId                  = Some("MSG123456"),
      correlationId              = Some("COR123456"),
      cipherText                 = Some("stub-cipher-text"),
      encryptionStatus           = Some("N"),
      updateSeqNumber            = 30,
      applicationStatus          = Some("D"),
      submissionStatus           = Some("S")
    )

  def getApplicationDetails(applicationId: Long): Action[AnyContent] = Action { implicit request =>
    logger.info(s"Stub: returning application for submission: $applicationId")

    if (applicationId % 1000 == 400) BadRequest("Simulated invalid application request")
    else if (applicationId % 1000 == 500) InternalServerError("Simulated database connectivity failure")
    else Ok(Json.toJson(ApplicationDetailsResponse(List(applicationDetails(applicationId)))))
  }

  private val getAttachmentDetailsResponse: GetAttachmentDetailsResponse = GetAttachmentDetailsResponse(
    attachmentNumber     = 11,
    fileName             = Some("test.pdf"),
    fileType             = Some("pdf"),
    fileDescription      = Some("pdf file attached"),
    fileSize             = Some(42),
    updateSequenceNumber = 10
  )

  def getAttachmentDetails(applicationId: Long, attachmentNumber: Int): Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning attachment details")

    // Simulate backend/database connectivity failure when applicationId ends with 500
    if (applicationId % 1000 == 500) InternalServerError("Simulated database connectivity failure")
    else if (attachmentNumber == 500)
      InternalServerError(s"No attachment found for the requested applicationId: $applicationId, attachmentNumber: $attachmentNumber")
    else Ok(Json.toJson(getAttachmentDetailsResponse.copy(attachmentNumber = attachmentNumber)))
  }

  private val attachment: Attachment = Attachment(
    attachmentNumber = 1,
    fileName         = Some("invoice.pdf"),
    fileType         = Some("pdf"),
    fileDescription  = Some("Invoice description"),
    fileSize         = Some(1024)
  )

  def getAllAttachments: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning all attachments")

    request.body.asJson.flatMap(_.validate[GetAllAttachmentsRequest].asOpt).fold(BadRequest("Invalid or missing request body")) { request =>
      request.applicationId match {
        case 0 => BadRequest(s"No attachments found for the requested applicationId: ${request.applicationId}")
        // Simulate backend/database connectivity failure when applicationId ends with 500
        case applicationId if applicationId % 1000 == 500 => InternalServerError("Simulated database connectivity failure")
        case _ =>
          val allAttachments: List[Attachment] =
            request.applicationId match {
              case 666 => List.empty
              case 111 => List(attachment.copy(fileDescription = None))
              case 402 =>
                (1 to 12).toList.map { number =>
                  attachment.copy(
                    attachmentNumber = number,
                    fileName         = Some(s"invoice-$number.pdf"),
                    fileDescription  = Some(s"Invoice $number")
                  )
                }
              case _ =>
                List(
                  attachment,
                  attachment.copy(
                    attachmentNumber = 2,
                    fileName         = Some("fuel-receipt.jpg"),
                    fileType         = Some("jpg"),
                    fileDescription  = Some("Fuel receipt"),
                    fileSize         = Some(2048)
                  ),
                  attachment.copy(
                    attachmentNumber = 3,
                    fileName         = Some("train-ticket.png"),
                    fileType         = Some("png"),
                    fileDescription  = None,
                    fileSize         = Some(512)
                  )
                )
            }

          val startAt = request.startAt.getOrElse(0)
          val maxNumber = request.maxNumber.getOrElse(10)

          Ok(
            Json.toJson(
              GetAllAttachmentsResponse(
                attachments      = allAttachments.slice(startAt, startAt + maxNumber),
                totalAttachments = allAttachments.size
              )
            )
          )
      }
    }
  }

  private val stubPdfBase64: String =
    "JVBERi0xLjQKMSAwIG9iago8PCAvVHlwZSAvQ2F0YWxvZyAvUGFnZXMgMiAwIFIgPj4KZW5kb2JqCjIgMCBvYmoKPDwgL1R5cGUgL1BhZ2VzIC9LaWRzIFszIDAgUl0gL0NvdW50IDEgPj4KZW5kb2JqCjMgMCBvYmoKPDwgL1R5cGUgL1BhZ2UgL1BhcmVudCAyIDAgUiAvTWVkaWFCb3ggWzAgMCAzMDAgMTQ0XSAvQ29udGVudHMgNCAwIFIgL1Jlc291cmNlcyA8PCAvRm9udCA8PCAvRjEgNSAwIFIgPj4gPj4gPj4KZW5kb2JqCjQgMCBvYmoKPDwgL0xlbmd0aCA1MiA+PgpzdHJlYW0KQlQgL0YxIDE4IFRmIDQwIDcwIFRkIChFVSBWQVQgc3R1YiBhdHRhY2htZW50KSBUaiBFVAplbmRzdHJlYW0KZW5kb2JqCjUgMCBvYmoKPDwgL1R5cGUgL0ZvbnQgL1N1YnR5cGUgL1R5cGUxIC9CYXNlRm9udCAvSGVsdmV0aWNhID4+CmVuZG9iagp4cmVmCjAgNgowMDAwMDAwMDAwIDY1NTM1IGYgCjAwMDAwMDAwMDkgMDAwMDAgbiAKMDAwMDAwMDA1OCAwMDAwMCBuIAowMDAwMDAwMTE1IDAwMDAwIG4gCjAwMDAwMDAyNDEgMDAwMDAgbiAKMDAwMDAwMDM0MyAwMDAwMCBuIAp0cmFpbGVyCjw8IC9TaXplIDYgL1Jvb3QgMSAwIFIgPj4Kc3RhcnR4cmVmCjQxMwolJUVPRgo="

  private val getFullAttachmentDetailsResponse: GetFullAttachmentDetailsResponse = GetFullAttachmentDetailsResponse(
    attachmentNumber     = 11,
    fileName             = Some("test.pdf"),
    fileType             = Some("pdf"),
    fileDescription      = Some("pdf file attached"),
    fileSize             = Some(596),
    fileContent          = Some(stubPdfBase64),
    updateSequenceNumber = 10
  )

  def getFullAttachmentDetails(applicationId: Long, attachmentNumber: Int): Action[AnyContent] = Action { implicit request =>
    logger.info(s"Stub: returning full attachment details for applicationId: $applicationId, attachmentNumber: $attachmentNumber")

    if (applicationId % 1000 == 500) InternalServerError("Simulated database connectivity failure")
    else if (attachmentNumber == 500)
      InternalServerError(s"No attachment found for the requested applicationId: $applicationId, attachmentNumber: $attachmentNumber")
    else Ok(Json.toJson(getFullAttachmentDetailsResponse.copy(attachmentNumber = attachmentNumber)))
  }

  def deleteAttachment: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: deleteAttachment called")

    request.body.asJson.flatMap(_.asOpt[DeleteAttachmentRequest]) match {
      case None                                     => BadRequest("Invalid or missing request body")
      case Some(req) if req.attachmentNumber == 500 => InternalServerError("Simulated database connectivity failure")
      case Some(req)                                =>
        // mimic the proxy: deleteAttachment bumps seq by 1
        val newSeq: Int = req.updateSequenceNumber + 1
        logger.info(s"Stub deleteAttachment: applicationId=${req.applicationId}, attachmentNumber=${req.attachmentNumber}, newSeq=$newSeq")
        Ok(Json.toJson(DeleteAttachmentResponse(newSeq)))
    }
  }

  def addAttachment: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: addAttachment called")

    request.body.asJson.flatMap(_.asOpt[AddAttachmentRequest]) match {
      case None =>
        BadRequest("Invalid or missing request body")

      case Some(req) if req.applicationId % 1000 == 500 =>
        InternalServerError("Simulated database connectivity failure")

      case Some(req) if req.applicationId <= 0 =>
        BadRequest(s"Invalid applicationId: ${req.applicationId}")

      case Some(req) =>
        val newSeq = req.updateSequenceNumber + 1
        val attachmentNumber = 1

        logger.info(
          s"Stub addAttachment: applicationId=${req.applicationId}, " +
            s"attachmentNumber=$attachmentNumber, newSeq=$newSeq"
        )

        Ok(
          Json.toJson(
            AddAttachmentResponse(
              attachmentNumber     = attachmentNumber,
              updateSequenceNumber = newSeq
            )
          )
        )
    }
  }
