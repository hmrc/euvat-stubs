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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.euvatstubs.models.responses.ApplicationResponse
import uk.gov.hmrc.euvatstubs.models.{LatestApplication, LatestApplicationResponse}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDateTime
import javax.inject.{Inject, Singleton}

@Singleton
class EuVatCandeDataController @Inject() (cc: ControllerComponents) extends BackendController(cc) with Logging:

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

  def getLatestApplications: Action[AnyContent] = Action { implicit request =>
    logger.info("Stub: returning latest Applications")

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
              !app.periodEndDate.isBefore(reqStart)
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
