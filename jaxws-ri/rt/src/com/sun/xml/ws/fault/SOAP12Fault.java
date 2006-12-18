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

package com.sun.xml.ws.fault;


import com.sun.xml.ws.api.SOAPVersion;
import org.w3c.dom.Node;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPFault;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.soap.SOAPFaultException;

/**
 * SOAP 1.2 Fault class that can be marshalled/unmarshalled by JAXB
 * <p/>
 * <pre>
 * Example:
 * &lt;env:Envelope xmlns:env="http://www.w3.org/2003/05/soap-envelope"
 *            xmlns:m="http://www.example.org/timeouts"
 *            xmlns:xml="http://www.w3.org/XML/1998/namespace">
 * &lt;env:Body>
 *     &lt;env:Fault>
 *         &lt;env:Code>
 *             &lt;env:Value>env:Sender* &lt;/env:Value>
 *             &lt;env:Subcode>
 *                 &lt;env:Value>m:MessageTimeout* &lt;/env:Value>
 *             &lt;/env:Subcode>
 *         &lt;/env:Code>
 *         &lt;env:Reason>
 *             &lt;env:Text xml:lang="en">Sender Timeout* &lt;/env:Text>
 *         &lt;/env:Reason>
 *         &lt;env:Detail>
 *             &lt;m:MaxTime>P5M* &lt;/m:MaxTime>
 *         &lt;/env:Detail>
 *     &lt;/env:Fault>
 * &lt;/env:Body>
 * &lt;/env:Envelope>
 * </pre>
 *
 * @author Vivek Pandey
 */
@XmlRootElement(name = "Fault", namespace = "http://www.w3.org/2003/05/soap-envelope")
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "Code",
    "Reason",
    "Node",
    "Role",
    "Detail"
})
class SOAP12Fault extends SOAPFaultBuilder {
    @XmlTransient
    private static final String ns = "http://www.w3.org/2003/05/soap-envelope";

    @XmlElement(namespace = ns)
    private CodeType Code;

    @XmlElement(namespace = ns)
    private ReasonType Reason;

    @XmlElement(namespace = ns)
    private String Node;

    @XmlElement(namespace = ns)
    private String Role;

    @XmlElement(namespace = ns)
    private DetailType Detail;

    SOAP12Fault() {
    }

    SOAP12Fault(CodeType code, ReasonType reason, String node, String role, DetailType detail) {
        Code = code;
        Reason = reason;
        Node = node;
        Role = role;
        Detail = detail;
    }

    SOAP12Fault(SOAPFault fault) {
        Code = new CodeType(fault.getFaultCodeAsQName());
        if (Code != null) {
            try {
                fillFaultSubCodes(fault, Code.getSubcode());
            } catch (SOAPException e) {
                throw new WebServiceException(e);
            }
        }

        Reason = new ReasonType(fault.getFaultString());
        Role = fault.getFaultRole();
        Detail = new DetailType(fault.getDetail());
    }

    SOAP12Fault(QName code, String reason, String actor, Node detailObject) {
        Code = new CodeType(code);
        Reason = new ReasonType(reason);
        if(detailObject != null)
            Detail = new DetailType(detailObject);
    }

    CodeType getCode() {
        return Code;
    }

    ReasonType getReason() {
        return Reason;
    }

    String getNode() {
        return Node;
    }

    String getRole() {
        return Role;
    }

    @Override
    DetailType getDetail() {
        return Detail;
    }
    @Override
    String getFaultString() {
        return Reason.texts().get(0).getText();
    }

     protected Throwable getProtocolException() {
        try {
            SOAPFault fault = SOAPVersion.SOAP_12.saajSoapFactory.createFault(Reason.texts().get(0).getText(), (Code != null)?Code.getValue():null);
            if(Detail != null && Detail.getDetail(0) instanceof Node){
                javax.xml.soap.Detail detail = fault.addDetail();
                for(Object obj:Detail.getDetails()){
                    if(!(obj instanceof Node))
                        continue;
                    Node n = fault.getOwnerDocument().importNode((Node)obj, true);
                    detail.appendChild(n);
                }
            }
            if(Code != null)
                fillFaultSubCodes(fault, Code.getSubcode());

            return new SOAPFaultException(fault);
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }
    }

    /**
     * Recursively populate the Subcodes
     */
    private void fillFaultSubCodes(SOAPFault fault, SubcodeType subcode) throws SOAPException {
        if(subcode != null){
            fault.appendFaultSubcode(subcode.getValue());
            fillFaultSubCodes(fault, subcode.getSubcode());
        }
        return;
    }
}

