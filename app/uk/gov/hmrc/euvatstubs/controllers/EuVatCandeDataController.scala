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
import play.api.mvc.{Action, AnyContent, ControllerComponents, RequestHeader, Result}
import uk.gov.hmrc.euvatstubs.models.requests.*
import uk.gov.hmrc.euvatstubs.models.responses.*
import uk.gov.hmrc.euvatstubs.models.LatestApplication
import uk.gov.hmrc.euvatstubs.repositories.{PurchaseImportRepository, VrnStateRepository}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class EuVatCandeDataController @Inject() (
  cc: ControllerComponents,
  vrnStateRepository: VrnStateRepository,
  purchaseImportRepository: PurchaseImportRepository
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging:

  private def sessionId(request: RequestHeader): String = request.headers.get("X-Session-ID").getOrElse("anonymous")

  def addApplication(vrn: String): Action[AnyContent] = Action.async { implicit request =>
    logger.info(s"Stub: Creating refund application for vrn: $vrn")

    val response = if (vrn.endsWith("111")) { ApplicationResponse(111, "GB123111", 1) }
    else if (vrn.endsWith("666")) { ApplicationResponse(666, "GB123666", 3) }
    else if (vrn.endsWith("999")) { ApplicationResponse(999, "GB123999", 2) }
    else if (vrn.endsWith("880")) { ApplicationResponse(402, "GB123406", 4) }
    else { ApplicationResponse(660, "GB123100", 6) }

    purchaseImportRepository.deletePurchaseDetails(sessionId(request), response.applicationId.toLong).map(_ => Ok(Json.toJson(response)))
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

  def addPurchase(): Action[AnyContent] = Action.async { implicit request =>
    logger.info("Stub: adding purchase")

    request.body.asJson.flatMap(_.asOpt[AddPurchaseRequest]) match {
      case None => Future.successful(BadRequest("Invalid or missing request body"))
      case Some(req) =>
        val purchaseDetails = UpdatePurchaseDetailsRequest(
          applicationId               = req.applicationId,
          itemNumber                  = 0,
          goodsDescriptionCategory    = req.goodsDescriptionCategory,
          goodsDescriptionSubCategory = req.purchaseSubcategory,
          goodsDescriptionText        = req.goodsDescriptionText,
          simplifiedInvoiceIndicator  = req.simplifiedInvoiceIndicator,
          supplierName                = req.supplierName,
          supplierAddress1            = req.supplierAddress1,
          supplierAddress2            = req.supplierAddress2,
          supplierAddress3            = req.supplierAddress3,
          supplierVatRegNumber        = req.supplierVatRegNumber,
          supplierTaxIdentifier       = req.supplierTaxIdentifier,
          invoiceDate                 = req.invoiceDate,
          invoiceNumber               = req.invoiceNumber,
          currencyCode                = req.currencyCode,
          taxableAmount               = req.taxableAmount,
          vatAmount                   = req.vatAmount,
          deductibleVatAmount         = req.deductibleVatAmount,
          updateSequenceNumber        = req.updateSequenceNumber
        )

        purchaseImportRepository.seedDefaultPurchaseDetails(sessionId(request), req.applicationId).flatMap { _ =>
          purchaseImportRepository.savePurchaseDetails(sessionId(request), req.applicationId, purchaseDetails).map { savedDetails =>
            Ok(Json.toJson(AddPurchaseResponse(itemNumber = savedDetails.itemNumber, updateSequenceNumber = savedDetails.updateSequenceNumber)))
          }
        }
    }
  }

  def addImport: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: adding import (rds-cande-proxy)")

    request.body.asJson.flatMap(_.asOpt[AddImportRequest]) match {
      case None => BadRequest("Invalid or missing request body")
      case Some(req) =>
        if (req.applicationId % 1000 == 500) InternalServerError("Simulated database connectivity failure")
        else Ok(Json.toJson(AddPurchaseResponse(itemNumber = 4, updateSequenceNumber = req.updateSequenceNumber)))
    }
  }

  def getPurchaseDetails: Action[AnyContent] = Action.async { implicit request =>
    logger.info("Stub: returning purchase details")

    request.body.asJson.flatMap(_.asOpt[GetPurchaseDetailsRequest]) match {
      case Some(req) =>
        getPurchaseDetailsForSession(sessionId(request), req.applicationId, req.itemNumber)
      case None => Future.successful(BadRequest("Invalid or missing request body"))
    }
  }

  private val importDetailsResponse: GetImportDetailsResponse = GetImportDetailsResponse(
    applicationId               = 404,
    itemNumber                  = 4,
    issuingDate                 = Some(LocalDateTime.of(2026, 5, 14, 0, 0)),
    supplierName                = Some("Finnish International"),
    supplierAddressLine1        = Some("356 High Street"),
    supplierAddressLine2        = Some("Rochdale"),
    supplierAddressLine3        = Some("England"),
    supplierCountryCode         = Some("GB"),
    goodsDescriptionCategory    = "10",
    goodsDescriptionSubcategory = Some("10.4.1"),
    goodsDescriptionText        = Some(""),
    hasSadReferenceNumber       = Some("Y"),
    sadReferenceNumber          = Some("SAD123456789"),
    referenceInformation        = Some("Import declaration reference"),
    currencyCode                = Some("EUR"),
    taxableAmount               = Some(BigDecimal("1000")),
    vatAmount                   = Some(BigDecimal("99")),
    deductibleVatAmount         = Some(BigDecimal("40")),
    updateSequenceNumber        = 1,
    periodStartDate             = LocalDateTime.of(2026, 1, 1, 0, 0)
  )

  def getImportDetails(applicationId: Long, itemNumber: Int): Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning import details")

    // Simulate backend/database connectivity failure when applicationId ends with 500
    if (applicationId % 1000 == 500) InternalServerError("Simulated database connectivity failure")
    else if (applicationId == 0) BadRequest(s"No import found for the requested applicationId: $applicationId")
    else Ok(Json.toJson(importDetailsResponse.copy(applicationId = applicationId, itemNumber = itemNumber)))
  }

  def getPurchaseDetailsByPath(applicationId: Long, itemNumber: Int): Action[AnyContent] = Action.async { implicit request =>
    logger.info("Stub: returning purchase details")
    getPurchaseDetailsForSession(sessionId(request), applicationId, itemNumber)
  }

  private def getPurchaseDetailsForSession(sessionId: String, applicationId: Long, itemNumber: Int): Future[Result] =
    purchaseImportRepository.seedDefaultPurchaseDetails(sessionId, applicationId).flatMap { _ =>
      purchaseImportRepository.findPurchaseDetails(sessionId, applicationId, itemNumber).map {
        case Some(savedDetails) => Ok(Json.toJson(toPurchaseDetailsResponse(savedDetails)))
        case None               => NotFound("Purchase details not found")
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

  def updatePurchaseDetails(): Action[AnyContent] = Action.async { implicit request =>
    logger.info("Stub: updatePurchaseDetails called")
    val bodyOpt = request.body.asJson

    bodyOpt match {
      case None       => Future.successful(BadRequest("Invalid or missing request body"))
      case Some(json) =>
        // invoice-only simulated errors
        (json \ "invoiceNumber").asOpt[String] match {
          case Some("INV-UP-500") => Future.successful(InternalServerError("simulated 5xx"))
          case Some("INV-UP-400") => Future.successful(BadRequest("simulated 4xx"))
          case _ =>
            json.asOpt[UpdatePurchaseDetailsRequest] match {
              case Some(req) =>
                purchaseImportRepository.savePurchaseDetails(sessionId(request), req.applicationId, req).map { savedDetails =>
                  Ok(Json.obj("updateSequenceNumber" -> savedDetails.updateSequenceNumber))
                }

              case None =>
                val applicationIdOpt: Option[Long] = (json \ "applicationId").asOpt[Long]
                val goodsDescriptionCategoryOpt: Option[String] = (json \ "goodsDescriptionCategory").asOpt[String]
                val goodsDescriptionTextOpt: Option[String] = (json \ "goodsDescriptionText").asOpt[String]
                val purchaseSubcategoryOpt: Option[String] = (json \ "goodsDescriptionSubCategory")
                  .asOpt[String]
                  .orElse((json \ "purchaseSubcategory").asOpt[String])
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
                      applicationId               = applicationId,
                      itemNumber                  = itemNumberOpt.getOrElse(0),
                      goodsDescriptionCategory    = goodsDescriptionCategory,
                      goodsDescriptionSubCategory = purchaseSubcategoryOpt,
                      goodsDescriptionText        = goodsDescriptionTextOpt,
                      simplifiedInvoiceIndicator  = simplifiedInvoiceIndicatorOpt,
                      supplierName                = supplierNameOpt,
                      supplierAddress1            = supplierAddress1Opt,
                      supplierAddress2            = supplierAddress2Opt,
                      supplierAddress3            = supplierAddress3Opt,
                      supplierVatRegNumber        = supplierVatRegNumberOpt,
                      supplierTaxIdentifier       = supplierTaxIdentifierOpt,
                      invoiceDate                 = invoiceDateOpt,
                      invoiceNumber               = invoiceNumberOpt,
                      currencyCode                = currencyCodeOpt,
                      taxableAmount               = taxableAmountOpt,
                      vatAmount                   = vatAmountOpt,
                      deductibleVatAmount         = deductibleVatAmountOpt,
                      updateSequenceNumber        = updateSeq
                    )

                    purchaseImportRepository.savePurchaseDetails(sessionId(request), req.applicationId, req).map { savedDetails =>
                      Ok(Json.obj("updateSequenceNumber" -> savedDetails.updateSequenceNumber))
                    }

                  case _ => Future.successful(BadRequest("Invalid or missing request body"))
                }
            }
        }
    }
  }

  private def toPurchaseDetailsResponse(savedDetails: UpdatePurchaseDetailsRequest): GetPurchaseDetailsResponse = GetPurchaseDetailsResponse(
    goodsDescriptionCode       = savedDetails.goodsDescriptionCategory,
    goodsDescriptionSubCode    = savedDetails.goodsDescriptionSubCategory,
    goodsDescriptionText       = savedDetails.goodsDescriptionText,
    simplifiedInvoiceIndicator = savedDetails.simplifiedInvoiceIndicator,
    supplierName               = savedDetails.supplierName,
    supplierAddressLine1       = savedDetails.supplierAddress1,
    supplierAddressLine2       = savedDetails.supplierAddress2,
    supplierAddressLine3       = savedDetails.supplierAddress3,
    supplierVatNumber          = savedDetails.supplierVatRegNumber,
    supplierTaxIdentifier      = savedDetails.supplierTaxIdentifier,
    invoiceDate                = savedDetails.invoiceDate,
    invoiceNumber              = savedDetails.invoiceNumber,
    currencyCode               = savedDetails.currencyCode,
    taxableAmount              = savedDetails.taxableAmount,
    vatAmount                  = savedDetails.vatAmount,
    deductibleVatAmount        = savedDetails.deductibleVatAmount,
    updateSequenceNumber       = savedDetails.updateSequenceNumber
  )

  def getPurchaseImportList: Action[AnyContent] = Action.async { implicit request =>
    logger.info("Stub: returning purchase and import list")

    request.body.asJson.flatMap(_.validate[PurchaseImportListRequest].asOpt).fold(Future.successful(BadRequest("Invalid or missing request body"))) {
      listRequest =>
        listRequest.applicationId match {
          case 0 => Future.successful(BadRequest(s"No items found for the requested applicationId: ${listRequest.applicationId}"))
          case _ =>
            purchaseImportRepository.seedDefaultPurchaseDetails(sessionId(request), listRequest.applicationId).flatMap { _ =>
              purchaseImportRepository.findPurchaseImports(sessionId(request), listRequest.applicationId).map { response =>
                Ok(
                  Json
                    .toJson(
                      PurchaseImportListResponse(
                        totalItems         = response.size,
                        purchaseImportList = response.sortBy(_.itemNumber),
                        totalVatClaims     = BigDecimal(456.78)
                      )
                    )
                )
              }
            }
        }
    }
  }

  def deleteApplication: Action[AnyContent] = Action.async { implicit request =>
    logger.info("Stub: deleteApplication called")
    val bodyOpt = request.body.asJson

    bodyOpt match {
      case None => Future.successful(BadRequest("Invalid or missing request body"))
      case Some(json) =>
        json.asOpt[DeleteApplicationRequest] match {
          case None      => Future.successful(BadRequest("Invalid or missing request body"))
          case Some(req) =>
            // Simulate a backend/database failure when applicationId ends with 500
            if (req.applicationId % 1000 == 500) Future.successful(InternalServerError("Simulated database connectivity failure"))
            else purchaseImportRepository.deletePurchaseDetails(sessionId(request), req.applicationId).map(_ => Ok)
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
