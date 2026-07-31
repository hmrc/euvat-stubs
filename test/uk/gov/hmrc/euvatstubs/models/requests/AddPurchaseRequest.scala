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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

class AddPurchaseRequestSpec extends AnyWordSpec with Matchers {

  private val request = AddPurchaseRequest(
    applicationId              = 123456,
    goodsDescriptionCategory   = Some("1"),
    goodsDescriptionText       = None,
    purchaseSubcategory        = None,
    simplifiedInvoiceIndicator = None,
    supplierName               = None,
    supplierAddress1           = None,
    supplierAddress2           = None,
    supplierAddress3           = None,
    supplierVatRegNumber       = None,
    supplierTaxIdentifier      = None,
    invoiceDate                = None,
    invoiceNumber              = None,
    currencyCode               = None,
    taxableAmount              = None,
    vatAmount                  = None,
    deductibleVatAmount        = None,
    updateSequenceNumber       = None
  )

  "AddPurchaseRequest JSON format" should {

    "serialize and deserialize correctly" in {
      Json.toJson(request).as[AddPurchaseRequest] shouldBe request
    }

    "deserialize when optional fields are absent" in {
      val result = Json.obj("applicationId" -> 123456).as[AddPurchaseRequest]
      result.applicationId shouldBe 123456L
      result.supplierName  shouldBe None
    }
  }
}
