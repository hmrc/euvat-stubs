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

case class UpdateImportDetailsRequest(
  applicationId: Long,
  itemNumber: Int,
  goodsDescriptionCategory: String,
  goodsDescriptionSubCategory: Option[String] = None,
  goodsDescriptionText: Option[String] = None,
  hasSadReferenceNumber: Option[String] = None,
  sadReferenceNumber: Option[String] = None,
  referenceInformation: Option[String] = None,
  supplierName: Option[String] = None,
  supplierAddressLine1: Option[String] = None,
  supplierAddressLine2: Option[String] = None,
  supplierAddressLine3: Option[String] = None,
  supplierCountryCode: Option[String] = None,
  issuingDate: Option[LocalDateTime] = None,
  currencyCode: Option[String] = None,
  taxableAmount: Option[BigDecimal] = None,
  vatAmount: Option[BigDecimal] = None,
  deductibleVatAmount: Option[BigDecimal] = None,
  updateSequenceNumber: Int
)

object UpdateImportDetailsRequest {
  implicit val format: OFormat[UpdateImportDetailsRequest] = Json.format[UpdateImportDetailsRequest]
}
