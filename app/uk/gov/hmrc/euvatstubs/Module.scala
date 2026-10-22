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

package uk.gov.hmrc.euvatstubs

import play.api.{Configuration, Environment, Mode}
import play.api.inject.{Binding, Module as AppModule}

import uk.gov.hmrc.euvatstubs.repositories.{InMemoryPurchaseImportRepository, MongoPurchaseImportRepository, PurchaseImportRepository}

import java.time.Clock

class Module extends AppModule:

  override def bindings(
    environment: Environment,
    configuration: Configuration
  ): Seq[Binding[_]] =
    val purchaseImportRepositoryBinding: Binding[PurchaseImportRepository] = environment.mode match
      case Mode.Test => bind[PurchaseImportRepository].to[InMemoryPurchaseImportRepository]
      case _         => bind[PurchaseImportRepository].to[MongoPurchaseImportRepository]

    bind[Clock].toInstance(
      Clock.systemDefaultZone
    ) :: purchaseImportRepositoryBinding :: Nil
