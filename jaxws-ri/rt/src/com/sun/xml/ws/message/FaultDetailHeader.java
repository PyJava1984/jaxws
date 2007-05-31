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
 * Copyright 2007 Sun Microsystems Inc. All Rights Reserved
 */

package com.sun.xml.ws.message;

import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.soap.SOAPMessage;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPHeader;
import javax.xml.soap.SOAPHeaderElement;
import javax.xml.namespace.QName;

import com.sun.istack.Nullable;
import com.sun.istack.NotNull;
import com.sun.xml.ws.api.addressing.AddressingVersion;
import com.sun.xml.stream.buffer.MutableXMLStreamBuffer;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

/**
 * @author Arun Gupta
 */
public class FaultDetailHeader extends AbstractHeaderImpl {

    private AddressingVersion av;
    private String wrapper;
    private String problemValue = null;

    public FaultDetailHeader(AddressingVersion av, String wrapper, QName problemHeader) {
        this.av = av;
        this.wrapper = wrapper;
        this.problemValue = problemHeader.toString();
    }

    public FaultDetailHeader(AddressingVersion av, String wrapper, String problemValue) {
        this.av = av;
        this.wrapper = wrapper;
        this.problemValue = problemValue;
    }

    public
    @NotNull
    String getNamespaceURI() {
        return av.nsUri;
    }

    public
    @NotNull
    String getLocalPart() {
        return av.faultDetailTag.getLocalPart();
    }

    @Nullable
    public String getAttribute(@NotNull String nsUri, @NotNull String localName) {
        return null;
    }

    public XMLStreamReader readHeader() throws XMLStreamException {
        MutableXMLStreamBuffer buf = new MutableXMLStreamBuffer();
        XMLStreamWriter w = buf.createFromXMLStreamWriter();
        writeTo(w);
        return buf.readAsXMLStreamReader();
    }

    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        w.writeStartElement("", av.faultDetailTag.getLocalPart(), av.faultDetailTag.getNamespaceURI());
        w.writeDefaultNamespace(av.nsUri);
        w.writeStartElement("", wrapper, av.nsUri);
        w.writeCharacters(problemValue);
        w.writeEndElement();
        w.writeEndElement();
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        SOAPHeader header = saaj.getSOAPHeader();
        SOAPHeaderElement she = header.addHeaderElement(av.faultDetailTag);
        she = header.addHeaderElement(new QName(av.nsUri, wrapper));
        she.addTextNode(problemValue);
    }

    public void writeTo(ContentHandler h, ErrorHandler errorHandler) throws SAXException {
        String nsUri = av.nsUri;
        String ln = av.faultDetailTag.getLocalPart();

        h.startPrefixMapping("",nsUri);
        h.startElement(nsUri,ln,ln,EMPTY_ATTS);
        h.startElement(nsUri,wrapper,wrapper,EMPTY_ATTS);
        h.characters(problemValue.toCharArray(),0,problemValue.length());
        h.endElement(nsUri,wrapper,wrapper);
        h.endElement(nsUri,ln,ln);
    }
}
