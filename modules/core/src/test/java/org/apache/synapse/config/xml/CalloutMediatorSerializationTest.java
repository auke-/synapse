/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *   * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.synapse.config.xml;

/**
 * Factory and Serializer tests for the Callout Mediator
 */
public class CalloutMediatorSerializationTest extends AbstractTestCase {

    private CalloutMediatorFactory calloutMediatorFactory;
    private CalloutMediatorSerializer calloutMediatorSerializer;

    public CalloutMediatorSerializationTest() {
        super(CacheMediatorSerializationTest.class.getName());
        calloutMediatorFactory = new CalloutMediatorFactory();
        calloutMediatorSerializer = new CalloutMediatorSerializer();
    }

    public void testCalloutMediatorSerializationScenarioOne() {
        String inputXml = "<callout xmlns=\"http://synapse.apache.org/ns/2010/04/configuration\" " +
                          "serviceURL=\"http://localhost:9000/soap/SimpleStockQuoteService\" " +
                          "action=\"urn:getQuote\"><source xmlns:s11=\"http://schemas.xmlsoap.org/" +
                          "soap/envelope/\" xmlns:s12=\"http://www.w3.org/2003/05/soap-envelope\" " +
                          "xpath=\"s11:Body/child::*[fn:position()=1] | s12:Body/child::*[fn:position()=1]\"/>" +
                          "<target xmlns:s11=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                          "xmlns:s12=\"http://www.w3.org/2003/05/soap-envelope\" xpath=\"s11:Body/" +
                          "child::*[fn:position()=1] | s12:Body/child::*[fn:position()=1]\"/></callout>";
        assertTrue(serialization(inputXml, calloutMediatorFactory, calloutMediatorSerializer));
        assertTrue(serialization(inputXml, calloutMediatorSerializer));
    }

    public void testCalloutMediatorSerializationScenarioTwo() {
        String inputXml = "<callout xmlns=\"http://synapse.apache.org/ns/2010/04/configuration\" " +
                          "serviceURL=\"http://localhost:9000/soap/SimpleStockQuoteService\" " +
                          "action=\"urn:getQuote\"><configuration axis2xml=\"axis2_custom.xml\" " +
                          "repository=\"path_to_repo\"/><source xmlns:s11=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                          "xmlns:s12=\"http://www.w3.org/2003/05/soap-envelope\" key=\"key1\"/>" +
                          "<target xmlns:s11=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                          "xmlns:s12=\"http://www.w3.org/2003/05/soap-envelope\" key=\"key2\"/></callout>";
        assertTrue(serialization(inputXml, calloutMediatorFactory, calloutMediatorSerializer));
        assertTrue(serialization(inputXml, calloutMediatorSerializer));
    }
}
