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

package org.apache.synapse.config.xml;

import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMAttribute;
import org.apache.synapse.Mediator;
import org.apache.synapse.mediators.builtin.RestMediator;

import javax.xml.namespace.QName;

/**
 * This creates a rest mediator instance
 *
 * <pre>
 * &lt;rest value="true|false"/&gt;
 * </pre>
 */
public class RestMediatorFactory implements MediatorFactory {

    private static final QName REST_Q = new QName(Constants.SYNAPSE_NAMESPACE, "rest");

    public Mediator createMediator(OMElement el) {
		RestMediator restMediator = new RestMediator();
		OMAttribute value = el.getAttribute(new QName(Constants.NULL_NAMESPACE, "value"));
		if (value != null) {
			String valueString = value.getAttributeValue();
			if (valueString.toLowerCase().equals("true")) {
				restMediator.setValue(true);
			} else {
				restMediator.setValue(false);
			}
		} 
        return restMediator;
    }

    public QName getTagQName() {
        return REST_Q;
    }
}
