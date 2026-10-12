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

case class GetImportDetailsResponse(
  applicationId: Long,
  itemNumber: Int,
  issuingDate: Option[LocalDateTime],
  supplierName: Option[String],
  supplierAddressLine1: Option[String],
  supplierAddress2: Option[String],
  supplierAddress3: Option[String],
  supplierCountryCode: Option[String],
  goodsDescriptionCategory: String,
  goodsDescriptionSubcategory: Option[String],
  goodsDescriptionText: Option[String],
  hasSadReferenceNumber: Option[String],
  sadReferenceNumber: Option[String],
  referenceInformation: Option[String],
  currencyCode: Option[String],
  taxableAmount: Option[BigDecimal],
  vatAmount: Option[BigDecimal],
  deductibleVatAmount: Option[BigDecimal],
  updateSequenceNumber: Int,
  periodStartDate: LocalDateTime
)

object GetImportDetailsResponse {
  implicit val format: OFormat[GetImportDetailsResponse] = Json.format[GetImportDetailsResponse]
}
