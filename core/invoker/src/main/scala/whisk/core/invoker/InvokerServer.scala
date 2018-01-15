/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package whisk.core.invoker

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Route
import whisk.common.{Logging, Scheduler, TransactionId}
import whisk.core.WhiskConfig
import whisk.core.connector.{MessagingProvider, PingMessage}
import whisk.core.entity.InstanceId
import whisk.http.BasicRasService
import whisk.spi.SpiLoader

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Failure

/**
 * Implements web server to handle certain REST API calls.
 * Currently provides a health ping route, only.
 */
class InvokerServer(implicit val ec: ExecutionContext, implicit val actorSystem: ActorSystem, implicit val logger: Logging) extends BasicRasService {

  override val instanceOrdinal = 1

  override def routes(implicit transid: TransactionId): Route = {
    super.routes ~ {
      (path("disable") & get) {
        Invoker.messageProducer match {
          case Some(producer) => {
            // Through negative number of invoker to pass unhealth message
            producer.send("health", PingMessage(InstanceId(-1 - Invoker.currentInstance.get.instance)))
            producer.close();
            Invoker.messageProducer=None;
            complete("Success disable invoker");
          }
          case None => complete("Can't disable invoker again")
        }
      }
    } ~ {
      (path("enable") & get) {
        Invoker.messageProducer match {
          case Some(_) => {
            complete("Can't enable invoker again");
          }
          case None =>{
            val msgProvider = SpiLoader.get[MessagingProvider]
            val healthProducer = msgProvider.getProducer(new WhiskConfig(Invoker.requiredProperties), ec)
            Scheduler.scheduleWaitAtMost(1.seconds)(() => {
              healthProducer.send("health", PingMessage(InstanceId(Invoker.currentInstance.get.instance))).andThen {
                case Failure(t) => logger.error(this, s"enable invoker, failed to ping the controller: $t")
              }
            })
            Invoker.messageProducer = Some(healthProducer)
            complete("Success enable invoker");
          }
        }
      }
    }
  }
}
