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

case class UpdateApplicationDetailsRequest(
  applicationId: Long,
  applicationLanguage: String,
  refundingCountry: String,
  periodStartDate: LocalDateTime,
  periodEndDate: LocalDateTime,
  applicantEmailAddress: String,
  applicantPhoneNumber: Option[String],
  representativeCountry: Option[String],
  representativeEmailAddress: Option[String],
  representativePhoneNumber: Option[String],
  bankAccountOwnerName: Option[String],
  bankAccountOwnerType: Option[String],
  ibanCode: Option[String],
  bicCode: Option[String],
  bankAccountCurrencyCode: Option[String],
  businessActivityCode2: Option[String],
  businessActivityCode3: Option[String],
  cipherText: Option[String],
  encryptionStatus: Option[String],
  updateSequenceNumber: Int
)

object UpdateApplicationDetailsRequest {
  implicit val format: OFormat[UpdateApplicationDetailsRequest] = Json.format[UpdateApplicationDetailsRequest]
}
