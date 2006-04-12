/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2006 Sun Microsystems Inc. All Rights Reserved
 */

package com.sun.xml.ws.model.wsdl;

import com.sun.xml.ws.api.model.wsdl.WSDLFault;
import com.sun.xml.ws.api.model.wsdl.WSDLMessage;

import javax.xml.namespace.QName;

/**
 * @author Vivek Pandey
 */
public class WSDLFaultImpl extends AbstractExtensibleImpl implements WSDLFault {
    private final String name;
    private final QName messageName;
    private WSDLMessageImpl message;

    public WSDLFaultImpl(String name, QName messageName) {
        this.name = name;
        this.messageName = messageName;
    }

    public String getName() {
        return name;
    }

    public WSDLMessageImpl getMessage() {
        return message;
    }

    void freeze(WSDLModelImpl root){
        message = root.getMessage(messageName);
    }
}
