/**
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * <p>
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.sp.tests;

import org.apache.log4j.Logger;
import org.sp.tests.base.SPBaseTest;
import org.sp.tests.util.HTTPResponse;
import org.sp.tests.util.TestResults;
import org.sp.tests.util.TestUtil;
import org.testng.Assert;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import java.net.URI;

import static org.sp.tests.util.Constants.DEFAULT_PASSWORD;
import static org.sp.tests.util.Constants.DEFAULT_USER_NAME;
import static org.sp.tests.util.Constants.HEADER_CONTTP_JSON;
import static org.sp.tests.util.Constants.HEADER_CONTTP_TEXT;
import static org.sp.tests.util.Constants.HTTP_GET;
import static org.sp.tests.util.Constants.HTTP_POST;

import static org.sp.tests.util.TestUtil.waitThread;

/**
 *
 * TestXMLInput class
 */
public class TestXMLInput extends SPBaseTest {
    private static final Logger log = Logger.getLogger(TestXMLInput.class);
    private URI msf4jbaseURI;
    //private final URI appApibaseURI = URI.create(SIDDHI_APP_API);
    private URI appApibaseURI;

    @BeforeTest
    public void testInit() {
        appApibaseURI = URI.create(spURL);
        msf4jbaseURI = URI.create(msf4jURL);
    }

    @Test
    public void testXmlInputMapping() {
        //Deploy siddhi app having input receiver with xml event mapper
        // and include http sink to send event data to msf4j service
        //String pubUrl = msf4jbaseURI + "/testresults";
        String pubUrl = "http://localhost:8080/testresults";
        String appbody = "@App:name('TestSiddhiAppxml')\n" +
                "@source(type='inMemory', topic='symbol', @map(type='xml'))\n" +
                "define stream FooStream (symbol string, price float, class string);\n" +
                "@Sink(type='http', publisher.url='" + pubUrl + "', method='{{method}}',headers='{{headers}}',\n" +
                "@map(type='json'))\n" +
                "define stream BarStream (message string,method String,headers String);\n" +
                "from FooStream#log()\n" +
                "select symbol as message, 'POST' as method, class as headers\n" +
                "insert into BarStream;";
        //Deploy siddhi app

        String appApipath = "/siddhi-apps";

        log.info("Deploying Siddhi App...");
        HTTPResponse httpResponse = TestUtil.sendHRequest(appbody,
                appApibaseURI, appApipath, HEADER_CONTTP_TEXT, HTTP_POST,
                true, DEFAULT_USER_NAME, DEFAULT_PASSWORD);
        //Assert whether siddhi app deployed - RestAPI call
        Assert.assertEquals(httpResponse.getResponseCode(), 201);
        //wait untill siddhi app get deployed
        waitThread(5000);

        //Test case name(fully qualified method name)
        String testName = "com.sp.test.VerifyXML";
        String querybody = "{\n" +
                "  \"streamName\": \"FooStream\",\n" +
                "  \"siddhiAppName\": \"TestSiddhiAppxml\",\n" +
                "  \"timestamp\": null,\n" +
                "  \"data\": [\n" +
                "   \"TestData\",\n" +
                "   5.0,\n" +
                "   \"cclassName:" + testName + "\"\n" +
                "  ]\n" +
                "}";
        String simulatorpath = "/simulation/single";

        //Send single event
        log.info("Publishing a single event");
        HTTPResponse queryresponse = TestUtil.sendHRequest(querybody,
                appApibaseURI, simulatorpath, HEADER_CONTTP_TEXT, HTTP_POST,
                false, DEFAULT_USER_NAME, DEFAULT_PASSWORD);
        //wait till events published
        waitThread(3000);
        //Event should published to msf4j service using above sink
        Assert.assertEquals(queryresponse.getResponseCode(), 200);
        Assert.assertEquals(queryresponse.getContentType(), HEADER_CONTTP_JSON);
        Assert.assertEquals(queryresponse.getMessage(),
                "{\"status\":\"OK\",\"message\":\"Single Event simulation started successfully\"}");

        //polling the get method of msf4j service

        final HTTPResponse msf4jgetrespns = TestUtil.sendHRequest("",
                msf4jbaseURI, "/testresults" + "/com.sp.test.VerifyXM", HEADER_CONTTP_TEXT, HTTP_GET,
                false, DEFAULT_USER_NAME, DEFAULT_PASSWORD);
        //expected result
        final String msgPattern = "{\"message\":\"TestData\",\"method\":\"POST\"," +
                "\"headers\":\"cclassName:com.sp.test.VerifyXML\"}";

        TestResults testResults = new TestResults() {
            @Override
            public void waitForResults(int retryCount, long interval) {
                super.verifyResult(interval, retryCount, msf4jgetrespns.getMessage());
            }

            @Override
            public boolean resultsFound(String eventMessage) {
                return msgPattern.equalsIgnoreCase(eventMessage);
            }
        };
        //verify event results
        testResults.waitForResults(10, 1000);
        Assert.assertEquals(msf4jgetrespns.getMessage(), msgPattern);
        //Delete siddhi app - optional
        HTTPResponse apiDeleterespns = TestUtil.sendHRequest("", appApibaseURI,
                "/siddhi-apps/TestSiddhiAppxml", HEADER_CONTTP_TEXT, "DELETE",
                true, DEFAULT_USER_NAME, DEFAULT_PASSWORD);
        //verify app deleted
        Assert.assertEquals(apiDeleterespns.getResponseCode(), 200);
    }

    @AfterTest
    public void tearDown() {
        //clearing msf4j service
        HTTPResponse msf4jclearrespns = TestUtil.sendHRequest("",
                msf4jbaseURI, "/testresults/clear", HEADER_CONTTP_TEXT, HTTP_POST,
                false, DEFAULT_USER_NAME, DEFAULT_PASSWORD);
        Assert.assertEquals(msf4jclearrespns.getResponseCode(), 204);

    }


}
