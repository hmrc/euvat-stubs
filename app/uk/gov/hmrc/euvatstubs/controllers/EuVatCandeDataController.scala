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
import uk.gov.hmrc.euvatstubs.models.requests.{AddPurchaseRequest, GetPurchaseDetailsRequest}
import uk.gov.hmrc.euvatstubs.models.responses.{AddPurchaseResponse, ApplicationResponse, GetPurchaseDetailsResponse}
import uk.gov.hmrc.euvatstubs.models.{LatestApplication, LatestApplicationResponse, SupplierVrnCountRequest, SupplierVrnCountResponse}
import uk.gov.hmrc.euvatstubs.repositories.VrnStateRepository
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDateTime
import scala.util.control.NonFatal
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class EuVatCandeDataController @Inject() (cc: ControllerComponents, vrnStateRepository: VrnStateRepository)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging:

  def addApplication(vrn: String): Action[AnyContent] = Action { implicit request =>
    logger.info(s"Stub: Creating refund application for vrn: $vrn")

    val response = if (vrn.endsWith("111")) {
      ApplicationResponse(111, "GB123111", 1)
    } else if (vrn.endsWith("999")) {
      ApplicationResponse(999, "GB123999", 1)
    } else if (vrn.endsWith("666")) {
      ApplicationResponse(666, "GB123666", 1)
    } else {
      ApplicationResponse(100, "GB123100", 3)
    }
    Ok(Json.toJson(response))
  }

  private def latestApplicationResponse(): LatestApplicationResponse = LatestApplicationResponse(
    applications = List(
      LatestApplication(
        applicationId        = 404,
        refundingCountryCode = "LV",
        periodStartDate      = LocalDateTime.of(2025, 2, 1, 0, 0),
        periodEndDate        = LocalDateTime.of(2025, 5, 31, 23, 59),
        applicationNumber    = "GB0000000000000404",
        applicationStatus    = Some("D"),
        submissionStatus     = Some("S"),
        applicationVersion   = LocalDateTime.of(2025, 4, 22, 0, 0, 0, 0)
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
      // accept applicantVatRegNumber as string or numeric
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
              periodStartDate      = LocalDateTime.of(2025, 1, 1, 0, 0),
              periodEndDate        = LocalDateTime.of(2025, 12, 31, 23, 59),
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

            Future.successful(Right(LatestApplicationResponse(filtered, filtered.size)))
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

  def addPurchase: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: adding purchase")
    request.body.asJson.flatMap(_.asOpt[AddPurchaseRequest]) match {
      case None    => BadRequest("Invalid or missing request body")
      case Some(_) => Ok(Json.toJson(AddPurchaseResponse(itemNumber = 4, updateSequenceNumber = 1)))
    }
  }

  private val purchaseDetailsResponse: GetPurchaseDetailsResponse = GetPurchaseDetailsResponse(
    goodsDescriptionCode       = "1",
    goodsDescriptionSubCode    = Some("1.1"),
    goodsDescriptionText       = Some("Fuel"),
    simplifiedInvoiceIndicator = Some("N"),
    supplierName               = Some("Supplier Ltd"),
    supplierAddressLine1       = Some("1 High Street"),
    supplierAddressLine2       = Some("Riga"),
    supplierAddressLine3       = None,
    supplierVatNumber          = Some("LV40003567907"),
    supplierTaxIdentifier      = None,
    invoiceDate                = Some(LocalDateTime.of(2025, 3, 15, 0, 0)),
    invoiceNumber              = Some("INV-001"),
    currencyCode               = Some("EUR"),
    taxableAmount              = Some(BigDecimal("100.50")),
    vatAmount                  = Some(BigDecimal("21.10")),
    deductibleVatAmount        = Some(BigDecimal("21.10")),
    updateSequenceNumber       = 1
  )

  def getPurchaseDetails: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning purchase details")
    request.body.asJson.flatMap(_.asOpt[GetPurchaseDetailsRequest]) match {
      case None    => BadRequest("Invalid or missing request body")
      case Some(_) => Ok(Json.toJson(purchaseDetailsResponse))
    }
  }

  def getSupplierTaxIdentifierCount: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: getSupplierTaxIdentifierCount called")

    val body = request.body.asJson

    val resultOpt: Option[Result] = body.flatMap { json =>
      // accept invoiceNumber and taxIdentifier values
      val invoiceOpt: Option[String] = (json \ "invoiceNumber").asOpt[String]
      val taxOpt: Option[String] = (json \ "taxIdentifier").asOpt[String]

      // invoice-only simulated errors
      val fromInvoiceSim: Option[Result] = invoiceOpt.flatMap {
        case "INV-500" => Some(InternalServerError("simulated 5xx"))
        case "INV-400" => Some(BadRequest("simulated 4xx"))
        case _         => None
      }

      // explicit (taxIdentifier, invoiceNumber) -> duplicateCount mapping
      val pairDupMap: Map[(String, String), Int] = Map(
        ("TID-0", "INV-0")  -> 0,
        ("TID-1", "INV-1")  -> 1,
        ("TID-2", "INV-2")  -> 2,
        ("TID123", "INV-1") -> 0
      )

      val fromPairDup: Option[Result] = for {
        t <- taxOpt
        i <- invoiceOpt
        n <- pairDupMap.get((t, i))
      } yield Ok(Json.obj("duplicateCount" -> n))

      fromInvoiceSim orElse fromPairDup orElse {
        taxOpt.map { tax =>
          tax match {
            case "500" => InternalServerError("simulated 5xx")
            case "400" => BadRequest("simulated 4xx")
            case _ =>
              val duplicateCount =
                if (tax.endsWith("111")) 1
                else if (tax.endsWith("999")) 2
                else if (tax.endsWith("666")) 3
                else 0

              Ok(Json.obj("duplicateCount" -> duplicateCount))
          }
        }
      }
    }

    resultOpt.getOrElse(BadRequest("taxIdentifier or invoiceNumber is missing or invalid"))
  }

  def getSupplierVrnCount: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning supplier VRN count")

    request.body.asJson.flatMap(_.validate[SupplierVrnCountRequest].asOpt) match {
      case None =>
        BadRequest("Invalid or missing request body")

      case Some(req) if req.vatNumber.endsWith("500") =>
        InternalServerError("Simulated database connectivity failure")

      case Some(req) =>
        // Duplicate only when the VRN suffix triggers AND the invoice number is the "known duplicate" value.
        // Changing either the VRN (to a non-111/222 suffix) or the invoice (away from DUP) clears the warning.
        val count =
          if (req.invoiceNumber == "DUP") {
            req.vatNumber.takeRight(3) match {
              case "111" => 1
              case "222" => 2
              case _     => 0
            }
          } else 0

        logger.info(s"Stub getSupplierVrnCount: vatNumber=${req.vatNumber}, invoiceNumber=${req.invoiceNumber}, count=$count")
        Ok(Json.toJson(SupplierVrnCountResponse(count)))
    }
  }
