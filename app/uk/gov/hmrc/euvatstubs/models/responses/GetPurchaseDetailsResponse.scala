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

case class GetPurchaseDetailsResponse(
  goodsDescriptionCode: String,
  goodsDescriptionSubCode: Option[String],
  goodsDescriptionText: Option[String],
  simplifiedInvoiceIndicator: Option[String],
  supplierName: Option[String],
  supplierAddressLine1: Option[String],
  supplierAddressLine2: Option[String],
  supplierAddressLine3: Option[String],
  supplierVatNumber: Option[String],
  supplierTaxIdentifier: Option[String],
  invoiceDate: Option[LocalDateTime],
  invoiceNumber: Option[String],
  currencyCode: Option[String],
  taxableAmount: Option[BigDecimal],
  vatAmount: Option[BigDecimal],
  deductibleVatAmount: Option[BigDecimal],
  updateSequenceNumber: Int
)

object GetPurchaseDetailsResponse {
  implicit val format: OFormat[GetPurchaseDetailsResponse] = Json.format[GetPurchaseDetailsResponse]
}
