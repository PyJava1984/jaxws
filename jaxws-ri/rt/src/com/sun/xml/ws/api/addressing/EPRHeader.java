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

package com.sun.xml.ws.api.addressing;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.message.AbstractHeaderImpl;
import com.sun.xml.ws.util.xml.XmlUtil;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMResult;

/**
 * Used to represent outbound endpoint reference header,
 * such as &lt;ReplyTo> and &lt;FaultTo>.
 * Used from {@link WSEndpointReference}.
 *
 * @author Kohsuke Kawaguchi
 * @see WSEndpointReference
 */
final class EPRHeader extends AbstractHeaderImpl {

    private final String nsUri,localName;
    private final WSEndpointReference epr;

    EPRHeader(QName tagName, WSEndpointReference epr) {
        this.nsUri = tagName.getNamespaceURI();
        this.localName = tagName.getLocalPart();
        this.epr = epr;
    }

    public @NotNull String getNamespaceURI() {
        return nsUri;
    }

    public @NotNull String getLocalPart() {
        return localName;
    }

    @Nullable
    public String getAttribute(@NotNull String nsUri, @NotNull String localName) {
        try {
            XMLStreamReader sr = epr.read("EndpointReference"/*doesn't matter*/);
            while(sr.getEventType()!= XMLStreamConstants.START_ELEMENT)
                sr.next();

            return sr.getAttributeValue(nsUri,localName);
        } catch (XMLStreamException e) {
            // since we are reading from buffer, this can't happen.
            throw new AssertionError(e);
        }
    }

    public XMLStreamReader readHeader() throws XMLStreamException {
        return epr.read(localName);
    }

    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        epr.writeTo(localName, w);
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        try {
            // TODO what about in-scope namespaces
            // Not very efficient consider implementing a stream buffer
            // processor that produces a DOM node from the buffer.
            Transformer t = XmlUtil.newTransformer();
            t.transform(epr.asSource(localName), new DOMResult(saaj.getSOAPHeader()));
        } catch (Exception e) {
            throw new SOAPException(e);
        }
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
        epr.writeTo(localName,contentHandler,errorHandler,true);
    }
}
