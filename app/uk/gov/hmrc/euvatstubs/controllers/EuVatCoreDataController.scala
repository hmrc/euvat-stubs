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

import play.api.Logging
import play.api.libs.json.{Json, Reads}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.euvatstubs.models.{LatestApplication, LatestApplicationResponse, TradersKnownFacts}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}

@Singleton
class EuVatCoreDataController @Inject() (cc: ControllerComponents) extends BackendController(cc) with Logging:

  private def knownFactsResponse(vrn: String,
                                 tradeClass: String,
                                 regDate: Option[LocalDateTime] = None,
                                 deRegDate: Option[LocalDateTime] = None
                                ): TradersKnownFacts = TradersKnownFacts(
    vatRegNumber           = vrn.toIntOption.getOrElse(0),
    traderName             = "TestData",
    addressLine1           = "Line 1",
    addressLine2           = "Line 2",
    addressLine3           = "Line 3",
    addressLine4           = "Line 4",
    addressLine5           = "Line 5",
    postCode               = "NE3 9TG",
    tradeClass             = tradeClass,
    dateOfRegistration     = regDate,
    dateOfDeregistration   = deRegDate,
    missingTraderIndicator = "N"
  )

  def getTraderByVrn(vrn: String): Action[AnyContent] = Action { implicit request =>
    logger.info(s"Stub: returning known facts for VRN: $vrn")

    val response = if (vrn.endsWith("111")) {
      knownFactsResponse(vrn, "1111")
    } else if (vrn.endsWith("999")) {
      knownFactsResponse(vrn, "9999", Some(LocalDateTime.of(2025, 3, 1, 0, 0, 0, 0)))
    } else if (vrn.endsWith("888")) {
      knownFactsResponse(vrn, "8888", deRegDate = Some(LocalDateTime.of(2025, 8, 31, 23, 59, 59, 999999999)))
    } else {
      knownFactsResponse(vrn, "7020", Some(LocalDateTime.of(2024, 1, 31, 0, 0, 0, 0)), Some(LocalDateTime.of(2025, 12, 31, 23, 59, 59, 999999999)))
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

  def getLatestApplications(): Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning latest Applications")
    logger.info(s"Stub: request body = ${request.body.asJson}")

    val body = request.body.asJson

    val response = body.flatMap { json =>
      (json \ "applicantVatRegNumber").asOpt[String].filter(_.nonEmpty) match {
        case None => None // will trigger BadRequest
        case Some(_) =>
          for {
            country   <- (json \ "refundingCountry").asOpt[String]
            startDate <- (json \ "startDate").asOpt[String]
            endDate   <- (json \ "endDate").asOpt[String]
          } yield {
            val reqStart = LocalDateTime.parse(startDate.take(19))
            val reqEnd = LocalDateTime.parse(endDate.take(19))
            val stubApps = latestApplicationResponse().applications.filter { app =>
              app.refundingCountryCode == country &&
              !app.periodStartDate.isAfter(reqEnd) &&
              !app.periodEndDate.isBefore(reqStart) &&
              app.applicationStatus.isDefined // exclude null applicationStatus like the DB does
            }
            LatestApplicationResponse(stubApps, stubApps.size)
          }
      }
    }

    response match {
      case Some(r) => Ok(Json.toJson(r))
      case None    => BadRequest("applicantVatRegNumber is missing or empty")
    }
  }
