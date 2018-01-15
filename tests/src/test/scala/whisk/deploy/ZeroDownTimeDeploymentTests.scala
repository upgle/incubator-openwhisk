package whisk.deploy

import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.jayway.restassured.RestAssured
import common._
import common.rest.HttpConnection
import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.junit.JUnitRunner
import pureconfig.loadConfigOrThrow

import scala.concurrent.duration.DurationInt
import whisk.utils.retry

import scala.util.Try


@RunWith(classOf[JUnitRunner])
class ZeroDownTimeDeploymentTests
  extends TestHelpers
    with Matchers
    with ScalaFutures
    with WskActorSystem
    with StreamLogging {

  implicit val materializer = ActorMaterializer()

  val controllerProtocol = loadConfigOrThrow[String]("whisk.controller.protocol")
  val controllerAddress = WhiskProperties.getBaseControllerAddress
  val allInvokerStatusUrl = s"$controllerProtocol://$controllerAddress/invokers"


  def getAllInvokerStatus() = {
    val connectionContext = HttpConnection.getContext(controllerProtocol)
    val response = Try {
      Http()
        .singleRequest(
          HttpRequest(uri = s"$allInvokerStatusUrl"),
          connectionContext = connectionContext)
        .futureValue
    }.toOption

    response.map { res =>
      (res.status, Unmarshal(res).to[String].futureValue)
    }

  }

  it should "disable invoker and enable invoker" in {
    val invokerAddress = WhiskProperties.getBaseInvokerAddress()
    val disableUrl = s"http://$invokerAddress/disable"
    val response = RestAssured.given().get(disableUrl)
    response.statusCode shouldBe 200
    // at most 15 seconds later, the invoker0's status will be down
    retry(
      {
        getAllInvokerStatus().get._2 should include("\"invoker0/0\":\"down\"")
      },
      3, Some(5000.milliseconds)
    )

    val enableUrl = s"http://$invokerAddress/enable"
    val response2 = RestAssured.given().get(enableUrl)
    response2.statusCode shouldBe 200
    retry(
      {
        getAllInvokerStatus().get._2 should include("\"invoker0/0\":\"up\"")
      },
      5, Some(1000.milliseconds)
    )
  }

}
