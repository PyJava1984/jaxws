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
    "code",
    "reason",
    "node",
    "role",
    "detail"
})
class SOAP12Fault extends SOAPFaultBuilder {
    @XmlTransient
    private static final String ns = "http://www.w3.org/2003/05/soap-envelope";

    @XmlElement(namespace=ns, name="Code")
    private CodeType code;

    @XmlElement(namespace=ns, name="Reason")
    private ReasonType reason;

    @XmlElement(namespace=ns, name="Node")
    private String node;

    @XmlElement(namespace=ns, name="Role")
    private String role;

    @XmlElement(namespace=ns, name="Detail")
    private DetailType detail;

    SOAP12Fault() {
    }

    SOAP12Fault(CodeType code, ReasonType reason, String node, String role, DetailType detail) {
        this.code = code;
        this.reason = reason;
        this.node = node;
        this.role = role;
        this.detail = detail;
    }

    SOAP12Fault(SOAPFault fault) {
        code = new CodeType(fault.getFaultCodeAsQName());
        try {
            fillFaultSubCodes(fault, code.getSubcode());
        } catch (SOAPException e) {
            throw new WebServiceException(e);
        }

        reason = new ReasonType(fault.getFaultString());
        role = fault.getFaultRole();
        detail = new DetailType(fault.getDetail());
    }

    SOAP12Fault(QName code, String reason, String actor, Node detailObject) {
        this.code = new CodeType(code);
        this.reason = new ReasonType(reason);
        if(detailObject != null)
            detail = new DetailType(detailObject);
    }

    CodeType getCode() {
        return code;
    }

    ReasonType getReason() {
        return reason;
    }

    String getNode() {
        return node;
    }

    String getRole() {
        return role;
    }

    @Override
    DetailType getDetail() {
        return detail;
    }

    @Override
    void setDetail(DetailType detail) {
        this.detail = detail;
    }

    @Override
    String getFaultString() {
        return reason.texts().get(0).getText();
    }

     protected Throwable getProtocolException() {
        try {
            SOAPFault fault = SOAPVersion.SOAP_12.saajSoapFactory.createFault(reason.texts().get(0).getText(), (code != null)? code.getValue():null);
            if(detail != null && detail.getDetail(0) != null){
                javax.xml.soap.Detail detail = fault.addDetail();
                for(Node obj: this.detail.getDetails()){
                    Node n = fault.getOwnerDocument().importNode(obj, true);
                    detail.appendChild(n);
                }
            }
            if(code != null)
                fillFaultSubCodes(fault, code.getSubcode());

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
    }
}

