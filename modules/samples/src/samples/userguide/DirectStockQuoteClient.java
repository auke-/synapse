/*
* Copyright 2004,2005 The Apache Software Foundation.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*      http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package samples.userguide;

import org.apache.axiom.om.OMElement;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.context.MessageContextConstants;
import samples.common.StockQuoteHandler;

public class DirectStockQuoteClient {

    public static void main(String[] args) {

        String symbol  = "IBM";
        String turl    = "http://localhost:9000/axis2/services/SimpleStockQuoteService";
        String sAction = "urn:getQuote";

        if (args.length > 0) symbol = args[0];
        if (args.length > 2) turl   = args[2];

        try {
            OMElement getQuote = StockQuoteHandler.createStandardRequestPayload(symbol);

            Options options = new Options();
            if (turl != null)
                options.setProperty(MessageContextConstants.TRANSPORT_URL, turl);
            options.setAction(sAction);

            ServiceClient serviceClient = new ServiceClient();
            serviceClient.setOptions(options);
            OMElement result = serviceClient.sendReceive(getQuote).getFirstElement();

            System.out.println("Standard :: Stock price = $" +
                StockQuoteHandler.parseStandardResponsePayload(result));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
