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

package uk.gov.hmrc.euvatstubs.repositories

import javax.inject.{Inject, Singleton}
// Mongo driver imports
import org.mongodb.scala.model.Filters.equal
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import scala.concurrent.{ExecutionContext, Future}

case class VrnCount(vrn: String, count: Int)
object VrnCount {
  implicit val format: Format[VrnCount] = Json.format[VrnCount]
}

@Singleton
class VrnStateRepository @Inject() (
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[VrnCount](
      mongoComponent = mongoComponent,
      collectionName = "vrnState",
      domainFormat   = VrnCount.format,
      indexes        = Seq.empty
    ) {

  def incrementAndGet(vrn: String): Future[Int] = {
    // Use domain-typed collection operations to avoid low-level Bson handling.
    collection.find(equal("vrn", vrn)).toFuture().flatMap { seq =>
      seq.headOption match {
        case Some(existing) =>
          val newCount = existing.count + 1
          collection.replaceOne(equal("vrn", vrn), VrnCount(vrn, newCount)).toFuture().map(_ => newCount)
        case None =>
          collection.insertOne(VrnCount(vrn, 1)).toFuture().map(_ => 1)
      }
    }
  }
}
