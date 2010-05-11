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

package org.apache.synapse.commons.evaluators.config;

import org.apache.axiom.om.OMElement;
import org.apache.synapse.commons.evaluators.*;

import javax.xml.namespace.QName;

public class EqualSerializer extends AbstractEvaluatorSerializer{
    public OMElement serialize(OMElement parent, Evaluator evaluator) throws EvaluatorException {
        if (!(evaluator instanceof EqualEvaluator)) {
            throw new IllegalArgumentException("Evalutor should be a EqualEvalutor");
        }

        EqualEvaluator equalEvaluator = (EqualEvaluator) evaluator;
        OMElement matchElement = fac.createOMElement(new QName(EvaluatorConstants.EQUAL));

        matchElement.addAttribute(fac.createOMAttribute(EvaluatorConstants.SOURCE, nullNS,
                equalEvaluator.getSource()));

        if (equalEvaluator.getType() == 1) {
            matchElement.addAttribute(fac.createOMAttribute(EvaluatorConstants.TYPE, nullNS,
                    EvaluatorConstants.URL));
        } else if (equalEvaluator.getType() == 2) {
            matchElement.addAttribute(fac.createOMAttribute(EvaluatorConstants.TYPE, nullNS,
                    EvaluatorConstants.PARAM));
        } else if (equalEvaluator.getType() == 3) {
            matchElement.addAttribute(fac.createOMAttribute(EvaluatorConstants.TYPE, nullNS,
                    EvaluatorConstants.HEADER));
        } else {
            String msg = "Unsupported type value: " + equalEvaluator.getType();
            log.error(msg);
            throw new EvaluatorException(msg);
        }

        matchElement.addAttribute(fac.createOMAttribute(EvaluatorConstants.VALUE, nullNS,
                equalEvaluator.getValue()));

        if (parent != null) {
            parent.addChild(matchElement);
        }

        return matchElement;
    }
}