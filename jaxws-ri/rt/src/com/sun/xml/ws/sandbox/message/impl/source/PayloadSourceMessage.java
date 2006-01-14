package com.sun.xml.ws.sandbox.message.impl.source;

import com.sun.xml.bind.marshaller.SAX2DOMEx;
import com.sun.xml.ws.encoding.soap.SOAPVersion;
import com.sun.xml.ws.api.message.HeaderList;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.MessageProperties;
import com.sun.xml.ws.sandbox.message.impl.AbstractMessageImpl;
import com.sun.xml.ws.sandbox.message.impl.stream.StreamMessage;
import com.sun.xml.ws.streaming.SourceReaderFactory;
import com.sun.xml.ws.streaming.XMLStreamReaderFactory;
import com.sun.xml.ws.util.ASCIIUtility;
import com.sun.xml.ws.util.xml.XmlUtil;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.LocatorImpl;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.WebServiceException;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Payloadsource message that can be constructed for the StreamSource, SAXSource and DOMSource.
 *
 * @author Vivek Pandey
 *
 */
public class PayloadSourceMessage extends AbstractMessageImpl {
    private Source src;
    private String localName;
    private String namespaceUri;
    private HeaderList headers;
    private MessageProperties properties;
    private SourceUtils sourceUtils;

    private final SOAPVersion soapVersion;
    private Message streamMessage;

    public PayloadSourceMessage(HeaderList headers, Source src, SOAPVersion soapVersion) {
        this.headers = headers;
        this.src = src;
        this.soapVersion = soapVersion;
        sourceUtils = new SourceUtils(src);
        if(src instanceof StreamSource){
            StreamSource streamSource = (StreamSource)src;
            streamMessage = new StreamMessage(null, XMLStreamReaderFactory.createXMLStreamReader(streamSource.getInputStream(), true));
        }
    }

    public PayloadSourceMessage(Source s, SOAPVersion soapVer) {
        this(null, s, soapVer);
    }

    public boolean hasHeaders() {
        if(headers == null)
            return false;
        return headers.size() > 0;
    }

    public HeaderList getHeaders() {
        if(headers == null)
            headers = new HeaderList();
        return headers;
    }

    public MessageProperties getProperties() {
        if(properties == null)
            return properties = new MessageProperties();
        return properties;
    }

    public String getPayloadLocalPart() {
        if(localName != null)
            return localName;

        if(sourceUtils.isStreamSource()){
            localName = streamMessage.getPayloadLocalPart();
        }else{
            QName name = sourceUtils.sniff(src);
            localName = name.getLocalPart();
            namespaceUri = name.getNamespaceURI();
        }
        return localName;
    }

    public String getPayloadNamespaceURI() {
        if(namespaceUri != null)
            return namespaceUri;

        if(sourceUtils.isStreamSource()){
            namespaceUri = streamMessage.getPayloadNamespaceURI();
        }else{
            QName name = sourceUtils.sniff(src);
            localName = name.getLocalPart();
            namespaceUri = name.getNamespaceURI();
        }
        return namespaceUri;
    }


    public Source readPayloadAsSource() {
        return src;
    }

    public SOAPMessage readAsSOAPMessage() throws SOAPException {
        SOAPMessage msg = soapVersion.saajFactory.createMessage();
        SAX2DOMEx s2d = new SAX2DOMEx(msg.getSOAPPart());
        try {
            writeTo(s2d, XmlUtil.DRACONIAN_ERROR_HANDLER);
        } catch (SAXException e) {
            throw new SOAPException(e);
        }

        return msg;
    }

    public void writeTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException{
        if(sourceUtils.isStreamSource()){
            streamMessage.writeTo(contentHandler, errorHandler);
            return;
        }

        String soapNsUri = soapVersion.nsUri;
        contentHandler.setDocumentLocator(NULL_LOCATOR);
        contentHandler.startDocument();
        contentHandler.startPrefixMapping("S",soapNsUri);
        contentHandler.startElement(soapNsUri,"Envelope","S:Envelope",EMPTY_ATTS);
        contentHandler.startElement(soapNsUri,"Header","S:Header",EMPTY_ATTS);
        if(hasHeaders()) {
            int len = headers.size();
            for( int i=0; i<len; i++ ) {
                // shouldn't JDK be smart enough to use array-style indexing for this foreach!?
                headers.get(i).writeTo(contentHandler,errorHandler);
            }
        }
        contentHandler.endElement(soapNsUri,"Header","S:Header");

        // write the body
        contentHandler.startElement(soapNsUri,"Body","S:Body",EMPTY_ATTS);

        writePayloadTo(contentHandler, errorHandler);
        contentHandler.endElement(soapNsUri,"Body","S:Body");
        contentHandler.endElement(soapNsUri,"Envelope","S:Envelope");
    }

    private void writePayloadTo(ContentHandler contentHandler, ErrorHandler errorHandler) throws SAXException{
        SAXResult sr = new SAXResult(contentHandler);
        try {
            Transformer transformer = XmlUtil.newTransformer();
            transformer.transform(src, sr);
        } catch (TransformerConfigurationException e) {
            errorHandler.fatalError(new SAXParseException(e.getMessage(),NULL_LOCATOR,e));
        } catch (TransformerException e) {
            errorHandler.fatalError(new SAXParseException(e.getMessage(),NULL_LOCATOR,e));
        }
    }

    public <T> T readPayloadAsJAXB(Unmarshaller unmarshaller) throws JAXBException {
        return (T)unmarshaller.unmarshal(src);
    }

    public XMLStreamReader readPayload() throws XMLStreamException {
        return SourceReaderFactory.createSourceReader(src, true);
    }

    public void writePayloadTo(XMLStreamWriter w) throws XMLStreamException {
        if(sourceUtils.isStreamSource()){
            streamMessage.writePayloadTo(w);
            return;
        }
        SourceUtils.serializeSource(src, w);
    }

    public void writeTo(XMLStreamWriter w) throws XMLStreamException {
        if(sourceUtils.isStreamSource()){
            streamMessage.writeTo(w);
            return;
        }
        String soapNsUri = soapVersion.nsUri;
        w.writeStartDocument();
       // w.writeNamespace("S",soapNsUri);
        w.writeStartElement("S","Envelope",soapNsUri);
        w.writeNamespace("S",soapNsUri);

        //write soapenv:Header
        w.writeStartElement("S","Header",soapNsUri);
        if(hasHeaders()) {
            int len = headers.size();
            for( int i=0; i<len; i++ ) {
                headers.get(i).writeTo(w);
            }
        }
        w.writeEndElement();

        // write the body
        w.writeStartElement("S","Body",soapNsUri);
        SourceUtils.serializeSource(src, w);
        w.writeEndElement();

        w.writeEndElement();
        w.writeEndDocument();
    }

    public Message copy() {
        Message msg = null;
        if(sourceUtils.isStreamSource()){
            StreamSource ss = (StreamSource)src;
            try {
                byte[] bytes = ASCIIUtility.getBytes(ss.getInputStream());
                ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
                StreamSource newSource = new StreamSource(bis, src.getSystemId());
                newSource.setReader(ss.getReader());
            } catch (IOException e) {
                throw new WebServiceException(e);
            }
        }else if(sourceUtils.isSaxSource()){
            SAXSource saxSrc = (SAXSource)src;
            InputSource is = saxSrc.getInputSource();
            InputSource newIs = is;
            if(is.getByteStream() != null){
                try {
                    byte[] bytes = ASCIIUtility.getBytes(is.getByteStream());
                    newIs = new InputSource(new ByteArrayInputStream(bytes));
                    newIs.setSystemId(is.getSystemId());
                    newIs.setEncoding(is.getEncoding());
                } catch (IOException e) {
                    throw new WebServiceException(e);
                }
            }else if(is.getCharacterStream() != null){
                newIs = new InputSource(is.getCharacterStream());
                newIs.setSystemId(is.getSystemId());
                newIs.setEncoding(is.getEncoding());
            }
            SAXSource newSaxSrc = new SAXSource(saxSrc.getXMLReader(), newIs);
            msg =  new PayloadSourceMessage(headers, newSaxSrc, soapVersion);
        }else if(sourceUtils.isDOMSource()){
            DOMSource ds = (DOMSource)src;
            try {
                SAX2DOMEx s2d = new SAX2DOMEx();
                writePayloadTo(s2d, XmlUtil.DRACONIAN_ERROR_HANDLER);
                Source newDomSrc = new DOMSource(s2d.getDOM(), ds.getSystemId());
                msg = new PayloadSourceMessage(headers, newDomSrc, soapVersion);
            } catch (ParserConfigurationException e) {
                throw new WebServiceException(e);
            } catch (SAXException e) {
                throw new WebServiceException(e);
            }
        }
        return msg;
    }

    private static final Attributes EMPTY_ATTS = new AttributesImpl();
    private static final LocatorImpl NULL_LOCATOR = new LocatorImpl();
}
