package whisk.deploy

import com.jayway.restassured.RestAssured
import common._
import org.junit.runner.RunWith
import org.scalatest.Matchers
import org.scalatest.junit.JUnitRunner
import scala.concurrent.duration.DurationInt
import whisk.utils.retry


@RunWith(classOf[JUnitRunner])
class ZeroDownTimeDeploymentTests extends TestHelpers with Matchers {


  it should "disable invoker and enable invoker" in {
    val invokerAddress = WhiskProperties.getBaseInvokerAddress()
    val disableUrl = s"http://$invokerAddress/disable"
    val response = RestAssured.given().get(disableUrl)
    response.statusCode shouldBe 200
    val controllerAddress = WhiskProperties.getBaseControllerAddress
    val allInvokerStatusUrl = s"http://$controllerAddress/invokers"
    // at most 15 seconds later, the invoker0's status will be down
    retry(
      { val response2 = RestAssured.given().get(allInvokerStatusUrl)
        response2.body().asString() should include("\"invoker0\":\"down\"")
      },
      3, Some(5000.milliseconds)
    )

    val enableUrl = s"http://$invokerAddress/enable"
    val response3 = RestAssured.given().get(enableUrl)
    response3.statusCode shouldBe 200
    retry(
      { val response4 = RestAssured.given().get(allInvokerStatusUrl)
        response4.body().asString() should include("\"invoker0\":\"up\"")
      },
      5, Some(1000.milliseconds)
    )
  }

}
