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
package com.sun.xml.ws.message.jaxb;

import com.sun.istack.NotNull;
import com.sun.xml.bind.api.Bridge;
import com.sun.xml.bind.api.BridgeContext;
import com.sun.xml.bind.api.JAXBRIContext;
import com.sun.xml.stream.buffer.XMLStreamBuffer;
import com.sun.xml.stream.buffer.XMLStreamBufferResult;
import com.sun.xml.ws.api.message.Header;
import com.sun.xml.ws.message.AbstractHeaderImpl;
import com.sun.xml.ws.message.RootElementSniffer;
import com.sun.xml.ws.util.exception.XMLStreamException2;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.util.JAXBResult;
import javax.xml.namespace.QName;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.OutputStream;
import java.util.Map;

/**
 * {@link Header} whose physical data representation is a JAXB bean.
 *
 * @author Kohsuke Kawaguchi
 */
public final class JAXBHeader extends AbstractHeaderImpl {

    /**
     * The JAXB object that represents the header.
     */
    private final Object jaxbObject;

    private final Bridge bridge;

    // information about this header. lazily obtained.
    private String nsUri;
    private String localName;
    private Attributes atts;

    /**
     * Once the header is turned into infoset,
     * this buffer keeps it.
     */
    private XMLStreamBuffer infoset;

    public JAXBHeader(JAXBRIContext context, Object jaxbObject) {
        this.jaxbObject = jaxbObject;
        this.bridge = new MarshallerBridge(context);

        if (jaxbObject instanceof JAXBElement) {
            JAXBElement e = (JAXBElement) jaxbObject;
            this.nsUri = e.getName().getNamespaceURI();
            this.localName = e.getName().getLocalPart();
        }
    }

    public JAXBHeader(Bridge bridge, Object jaxbObject) {
        this.jaxbObject = jaxbObject;
        this.bridge = bridge;

        QName tagName = bridge.getTypeReference().tagName;
        this.nsUri = tagName.getNamespaceURI();
        this.localName = tagName.getLocalPart();
    }

    /**
     * Lazily parse the first element to obtain attribute values on it.
     */
    private void parse() {
        RootElementSniffer sniffer = new RootElementSniffer();
        try {
            bridge.marshal(jaxbObject,sniffer);
        } catch (JAXBException e) {
            // if it's due to us aborting the processing after the first element,
            // we can safely ignore this exception.
            //
            // if it's due to error in the object, the same error will be reported
            // when the readHeader() method is used, so we don't have to report
            // an error right now.
            nsUri = sniffer.getNsUri();
            localName = sniffer.getLocalName();
            atts = sniffer.getAttributes();
        }
    }


    public @NotNull String getNamespaceURI() {
        if(nsUri==null)
            parse();
        return nsUri;
    }

    public @NotNull String getLocalPart() {
        if(localName==null)
            parse();
        return localName;
    }

    public String getAttribute(String nsUri, String localName) {
        if(atts==null)
            parse();
        return atts.getValue(nsUri,localName);
    }

    public XMLStreamReader readHeader() throws XMLStreamException {
        try {
            if(infoset==null) {
                XMLStreamBufferResult sbr = new XMLStreamBufferResult();
                bridge.marshal(jaxbObject,sbr);
                infoset = sbr.getXMLStreamBuffer();
            }
            return infoset.readAsXMLStreamReader();
        } catch (JAXBException e) {
            throw new XMLStreamException2(e);
        }
    }

    public <T> T readAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        JAXBResult r = new JAXBResult(unmarshaller);
        bridge.marshal(jaxbObject,r);
        return (T)r.getResult();
    }

    public <T> T readAsJAXB(Bridge<T> bridge) throws JAXBException {
        return bridge.unmarshal(new JAXBBridgeSource(this.bridge,jaxbObject));
    }

    public void writeTo(XMLStreamWriter sw) throws XMLStreamException {
        try {
            // If writing to Zephyr, get output stream and use JAXB UTF-8 writer
            if (sw instanceof Map) {
                OutputStream os = (OutputStream) ((Map) sw).get("sjsxp-outputstream");
                if (os != null) {
                    sw.writeCharacters("");        // Force completion of open elems
                    bridge.marshal(jaxbObject, os, sw.getNamespaceContext());
                    return;
                }
            }

            bridge.marshal(jaxbObject,sw);
        }
        catch (JAXBException e) {
            throw new XMLStreamException2(e);
        }
    }

    public void writeTo(SOAPMessage saaj) throws SOAPException {
        try {
            bridge.marshal(jaxbObject,saaj.getSOAPHeader());
        } catch (JAXBException e) {
            throw new SOAPException(e);
        }
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException {
        try {
            bridge.marshal(jaxbObject,contentHandler);
        } catch (JAXBException e) {
            SAXParseException x = new SAXParseException(e.getMessage(),null,null,-1,-1,e);
            errorHandler.fatalError(x);
            throw x;
        }
    }
}
