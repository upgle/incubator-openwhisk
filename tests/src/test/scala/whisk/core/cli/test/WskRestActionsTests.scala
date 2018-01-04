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

package whisk.core.cli.test

import akka.http.scaladsl.model.StatusCodes.OK

import org.junit.runner.RunWith
import org.scalatest.BeforeAndAfterAll
import org.scalatest.exceptions.TestFailedException
import org.scalatest.junit.JUnitRunner

import scala.concurrent.duration.DurationInt

import com.jayway.restassured.RestAssured

import common.rest.WskRest
import common.TestHelpers
import common.TestUtils
import common.WskProps
import common.WskTestHelpers
import system.rest.RestUtil
import whisk.utils.retry

/**
  * Tests actions via rest.
  */
@RunWith(classOf[JUnitRunner])
class WskRestActionsTests extends TestHelpers with WskTestHelpers with RestUtil with BeforeAndAfterAll {
    val wsk: common.rest.WskRest = new WskRest
    private implicit val wskprops = WskProps()
    val namespace = wsk.namespace.whois()

    protected val testRoutePath: String = "/api/v1/namespaces"

    behavior of "Wsk Actions"

    it should "save activation while option volatile is false or not passed" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
        val name = "echo"
        val file = Some(TestUtils.getTestActionFilename("echo.js"))
        val host = getServiceURL
        val url = host + s"$testRoutePath/$namespace/actions/$name"

        assetHelper.withCleaner(wsk.action, name) { (action, _) =>
            action.create(name, file)
        }

        val response = RestAssured
            .given()
            .header("content-type", "application/json")
            .config(sslconfig)
            .auth()
            .preemptive()
            .basic(wskprops.authKey.split(":")(0), wskprops.authKey.split(":")(1))
            .post(url)

        val activationId = Some(response.body.asString.replaceAll("[\\s\"{}]","").split(":")(1))
        retry({wsk.activation.get(activationId, expectedExitCode = OK.intValue)}, 10, Some(200.milliseconds))
    }

    it should "not save activation while option volatile is true" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
        val name = "echo"
        val file = Some(TestUtils.getTestActionFilename("echo.js"))
        val host = getServiceURL
        val url = host + s"$testRoutePath/$namespace/actions/$name?volatile=true"

        assetHelper.withCleaner(wsk.action, name) { (action, _) =>
            action.create(name, file)
        }

        val response = RestAssured
            .given()
            .header("content-type", "application/json")
            .config(sslconfig)
            .auth()
            .preemptive()
            .basic(wskprops.authKey.split(":")(0), wskprops.authKey.split(":")(1))
            .post(url)

        val activationId = Some(response.body.asString.replaceAll("[\\s\"{}]","").split(":")(1))
        the[TestFailedException] thrownBy{
          retry({wsk.activation.get(activationId, expectedExitCode = OK.intValue)}, 2, Some(1000.milliseconds))
        } should have message "404 was not equal to 200"
    }

    it should "save activation while error happens when invoking action even volatile is true" in withAssetCleaner(wskprops) { (wp, assetHelper) =>
        val name = "echoError"
        val file = Some(TestUtils.getTestActionFilename("echoError.js"))
        val host = getServiceURL
        val url = host + s"$testRoutePath/$namespace/actions/$name?volatile=true"

        assetHelper.withCleaner(wsk.action, name) { (action, _) =>
            action.create(name, file)
        }

        val response = RestAssured
            .given()
            .header("content-type", "application/json")
            .config(sslconfig)
            .auth()
            .preemptive()
            .basic(wskprops.authKey.split(":")(0), wskprops.authKey.split(":")(1))
            .post(url)

        val activationId = Some(response.body.asString.replaceAll("[\\s\"{}]","").split(":")(1))
        retry({wsk.activation.get(activationId, expectedExitCode = OK.intValue)}, 10, Some(200.milliseconds))
    }
}
