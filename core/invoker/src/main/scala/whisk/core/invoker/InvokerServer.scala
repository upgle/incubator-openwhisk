package whisk.core.invoker

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Route
import whisk.common.{Logging, Scheduler, TransactionId}
import whisk.core.WhiskConfig
import whisk.core.connector.{MessagingProvider, PingMessage}
import whisk.core.entity.InvokerInstanceId
import whisk.http.BasicRasService
import whisk.spi.SpiLoader

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Implements web server to handle certain REST API calls.
  * Currently provides a health ping route, only.
  */
class InvokerServer(implicit val ec: ExecutionContext, implicit val actorSystem: ActorSystem, implicit val logger: Logging) extends BasicRasService {

  val invokerUsername = {val source = scala.io.Source.fromFile("/conf/invokerauth.username");try source.mkString.replaceAll("\r|\n", "") finally source.close()}
  val invokerPassword= {val source = scala.io.Source.fromFile("/conf/invokerauth.password");try source.mkString.replaceAll("\r|\n", "") finally source.close()}

  override def routes(implicit transid: TransactionId): Route = {
    super.routes ~ {
      (path("disable") & get) {
        extractCredentials {
          case Some(BasicHttpCredentials(username, password)) =>
            if(username == invokerUsername && password == invokerPassword){
              Invoker.messageProducer match {
                case Some(producer) => {
                  // Through negative number of invoker to pass unhealthy message
                  producer.send("health", PingMessage(InvokerInstanceId(-1 - Invoker.currentInstance.get.instance, Invoker.currentInstance.get.name)), 3).andThen {
                    case Success(_) =>
                      producer.close();
                      logger.info(this, "send unhealthy message successfully")
                    case Failure(_) =>
                      logger.info(this, "failed to send unhealthy message")
                  }
                  Invoker.messageProducer=None;
                  complete("Success disable invoker");
                }
                case None => complete("Can't disable invoker again")
              }
            }else{
              complete("username or password is wrong")
            }
          case _ => complete(StatusCodes.Unauthorized)
        }
      }
    } ~ {
      (path("enable") & get) {
        extractCredentials {
          case Some(BasicHttpCredentials(username, password)) =>
            if(username == invokerUsername && password == invokerPassword){
              Invoker.messageProducer match {
                case Some(_) => {
                  complete("Can't enable invoker again");
                }
                case None =>{
                  val msgProvider = SpiLoader.get[MessagingProvider]
                  val healthProducer = msgProvider.getProducer(new WhiskConfig(Invoker.requiredProperties))
                  Scheduler.scheduleWaitAtMost(1.seconds)(() => {
                    healthProducer.send("health", PingMessage(InvokerInstanceId(Invoker.currentInstance.get.instance, Invoker.currentInstance.get.name))).andThen {
                      case Failure(t) => logger.error(this, s"enable invoker, failed to ping the controller: $t")
                    }
                  })
                  Invoker.messageProducer = Some(healthProducer)
                  complete("Success enable invoker");
                }
              }
            }else{
              complete("username or password is wrong")
            }
          case _ => complete(StatusCodes.Unauthorized)
        }
      }
    }
  }
}