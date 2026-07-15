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
import scala.util.control.NonFatal
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
    def mapVrnToSim(v: String): String = v match {
      case "111111115" => "SIM-5XX"
      case "111111111" => "SIM-DUP"
      case "222222222" => "SIM-OK"
      case "333333333" => "SIM-EMPTY"
      case "444444444" => "SIM-4XX"
      case other       => other
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
          case "SIM-5XX"   => Left(InternalServerError("simulated 5xx"))
          case "SIM-4XX"   => Left(BadRequest("simulated 4xx"))
          case "SIM-EMPTY" => Right(LatestApplicationResponse(Nil, 0))
          case "SIM-DUP"   =>
            // return a draft application (applicationStatus = D) which should trigger duplicate validation
            val app = LatestApplication(
              applicationId        = 1L,
              refundingCountryCode = (json \ "refundingCountry").asOpt[String].getOrElse("LV"),
              periodStartDate      = LocalDateTime.of(2025, 1, 1, 0, 0),
              periodEndDate        = LocalDateTime.of(2025, 12, 31, 23, 59),
              applicationNumber    = "GB-DUP-0001",
              applicationStatus    = Some("D"),
              submissionStatus     = None, // null submissionStatus to exercise null-path as well
              applicationVersion   = LocalDateTime.now()
            )
            Right(LatestApplicationResponse(List(app), 1))
          case "SIM-OK" =>
            val app = LatestApplication(
              applicationId        = 2L,
              refundingCountryCode = (json \ "refundingCountry").asOpt[String].getOrElse("LV"),
              periodStartDate      = LocalDateTime.of(2025, 1, 1, 0, 0),
              periodEndDate        = LocalDateTime.of(2025, 12, 31, 23, 59),
              applicationNumber    = "GB-OK-0001",
              applicationStatus    = Some("A"),
              submissionStatus     = Some("S"),
              applicationVersion   = LocalDateTime.now()
            )
            Right(LatestApplicationResponse(List(app), 1))
          case _ =>
            // default behaviour: if dates provided, filter by period; otherwise return default response
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

            Right(LatestApplicationResponse(filtered, filtered.size))
        }
      }
    }

    responseOpt match {
      case Some(Left(err)) => err
      case Some(Right(r))  => Ok(Json.toJson(r))
      case None            => BadRequest("applicantVatRegNumber is missing or empty")
    }
  }
