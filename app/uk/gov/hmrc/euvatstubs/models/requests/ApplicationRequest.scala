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

package uk.gov.hmrc.euvatstubs.models.requests

import play.api.libs.json.{Json, OFormat}

import java.time.LocalDateTime

case class ApplicationRequest(
  applicantVatRegNumber: String,
  refundingCountryCode: Option[String],
  periodStartDate: Option[LocalDateTime],
  periodEndDate: Option[LocalDateTime],
  applicantEmailAddress: Option[String],
  applicantTelephoneNumber: Option[String],
  applicationLanguage: Option[String],
  businessActivityCode1: Option[String],
  businessActivityCode2: Option[String],
  businessActivityCode3: Option[String],
  representativeId: Option[String],
  representativeCountryCode: Option[String],
  representativeEmailAddress: Option[String],
  representativeIdType: Option[String],
  representativeTelephoneNumber: Option[String],
  bankAccountOwnerName: Option[String],
  bankAccountOwnerType: Option[String],
  iBanCode: Option[String],
  bicCode: Option[String],
  bankAccountCurrencyCode: Option[String]
)

object ApplicationRequest {
  implicit val format: OFormat[ApplicationRequest] = Json.format[ApplicationRequest]
}
