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

package com.sun.xml.ws.message;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 * @author Arun Gupta
 */
public class RelatesToHeader extends StringHeader {
    protected String type;

    public RelatesToHeader(QName name, String mid, String type) {
        super(name, mid);
        this.type = type;
    }

    public RelatesToHeader(QName name, String mid) {
        super(name, mid);
    }

    public String getType() {
        return type;
    }

    @Override
    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        w.writeStartElement("", name.getLocalPart(), name.getNamespaceURI());
        w.writeDefaultNamespace(name.getNamespaceURI());
        if (type != null)
            w.writeAttribute("type", type);
        w.writeCharacters(value);
        w.writeEndElement();
    }

    @Override
    public void writeTo(SOAPMessage saaj) throws SOAPException {
        SOAPHeader header = saaj.getSOAPHeader();
        SOAPHeaderElement she = header.addHeaderElement(name);

        // todo: cache QName
        if (type != null)
            she.addAttribute(new QName(name.getNamespaceURI(), "type"), type);
        she.addTextNode(value);
    }
}
