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

package uk.gov.hmrc.euvatstubs.models.responses

import play.api.libs.json.{Json, OFormat}

import java.time.LocalDateTime

case class ApplicationDetails(
  applicationId: Long,
  applicationNumber: Option[String],
  refundingCountryCode: Option[String],
  applicationVersion: Option[LocalDateTime],
  periodStartDate: Option[LocalDateTime],
  periodEndDate: Option[LocalDateTime],
  applicantVatRegNumber: Option[String],
  applicantEmailAddress: Option[String],
  applicantTelephoneNumber: Option[String],
  representativeId: Option[String],
  representativeIdType: Option[String],
  representativeCountry: Option[String],
  representativeEmailAddress: Option[String],
  representativeTelephoneNum: Option[String],
  applicationLanguage: Option[String],
  bankAccountOwnerName: Option[String],
  bankAccountOwnerType: Option[String],
  ibanCode: Option[String],
  bicCode: Option[String],
  bankAccountCurrencyCode: Option[String],
  businessActivityCode1: Option[String],
  businessActivityCode2: Option[String],
  businessActivityCode3: Option[String],
  messageId: Option[String],
  correlationId: Option[String],
  cipherText: Option[String],
  encryptionStatus: Option[String],
  updateSeqNumber: Int,
  applicationStatus: Option[String],
  submissionStatus: Option[String]
)

object ApplicationDetails {
  implicit val format: OFormat[ApplicationDetails] = Json.format[ApplicationDetails]
}

case class ApplicationDetailsResponse(applicationDetails: List[ApplicationDetails])

object ApplicationDetailsResponse {
  implicit val format: OFormat[ApplicationDetailsResponse] = Json.format[ApplicationDetailsResponse]
}
