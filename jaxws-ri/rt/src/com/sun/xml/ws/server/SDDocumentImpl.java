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

package com.sun.xml.ws.server;

import com.sun.xml.ws.api.server.DocumentAddressResolver;
import com.sun.xml.ws.api.server.PortAddressResolver;
import com.sun.xml.ws.api.server.SDDocument;
import com.sun.xml.ws.api.server.SDDocumentFilter;
import com.sun.xml.ws.api.server.SDDocumentSource;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.ws.wsdl.parser.ParserUtil;
import com.sun.xml.ws.wsdl.parser.WSDLConstants;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * {@link SDDocument} implmentation.
 *
 * <p>
 * This extends from {@link SDDocumentSource} so that
 * JAX-WS server runtime code can use {@link SDDocument}
 * as {@link SDDocumentSource}.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
class SDDocumentImpl extends SDDocumentSource implements SDDocument {

    private static final String NS_XSD = "http://www.w3.org/2001/XMLSchema";
    private static final QName SCHEMA_INCLUDE_QNAME = new QName(NS_XSD, "include");
    private static final QName SCHEMA_IMPORT_QNAME = new QName(NS_XSD, "import");

    /**
     * Creates {@link SDDocument} from {@link SDDocumentSource}.
     * @param src WSDL document infoset
     * @param serviceName wsdl:service name
     * @param portTypeName
     *      The information about the port of {@link WSEndpoint} to which this document is built for.
     *      These values are used to determine which document is the concrete and abstract WSDLs
     *      for this endpoint.
     *
     * @return null
     *      Always non-null.
     */
    public static SDDocumentImpl create(SDDocumentSource src, QName serviceName, QName portTypeName) {
        URL systemId = src.getSystemId();

        try {
            // RuntimeWSDLParser parser = new RuntimeWSDLParser(null);
            XMLStreamReader reader = src.read(xif);
            try {
                XMLStreamReaderUtil.nextElementContent(reader);

                QName rootName = reader.getName();
                if(rootName.equals(WSDLConstants.QNAME_SCHEMA)) {
                    String tns = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_TNS);
                    Set<URL> importedDocs = new HashSet<URL>();
                    while (XMLStreamReaderUtil.nextContent(reader) != XMLStreamConstants.END_DOCUMENT) {
                         if (reader.getEventType() != XMLStreamConstants.START_ELEMENT)
                            continue;
                        QName name = reader.getName();
                        if (SCHEMA_INCLUDE_QNAME.equals(name) || SCHEMA_IMPORT_QNAME.equals(name)) {
                            String importedDoc = reader.getAttributeValue(null, "schemaLocation");
                            if (importedDoc != null) {
                                importedDocs.add(new URL(src.getSystemId(), importedDoc));
                            }
                        }
                    }
                    return new SchemaImpl(rootName,systemId,src,tns,importedDocs);
                } else if (rootName.equals(WSDLConstants.QNAME_DEFINITIONS)) {
                    String tns = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_TNS);

                    boolean hasPortType = false;
                    boolean hasService = false;
                    Set<URL> importedDocs = new HashSet<URL>();

                    // if WSDL, parse more
                    while (XMLStreamReaderUtil.nextContent(reader) != XMLStreamConstants.END_DOCUMENT) {
                         if(reader.getEventType() != XMLStreamConstants.START_ELEMENT)
                            continue;

                        QName name = reader.getName();
                        if (WSDLConstants.QNAME_PORT_TYPE.equals(name)) {
                            String pn = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
                            if (portTypeName != null) {
                                if(portTypeName.getLocalPart().equals(pn)&&portTypeName.getNamespaceURI().equals(tns)) {
                                    hasPortType = true;
                                }
                            }
                        } else if (WSDLConstants.QNAME_SERVICE.equals(name)) {
                            String sn = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
                            QName sqn = new QName(tns,sn);
                            if(serviceName.equals(sqn)) {
                                hasService = true;
                            }
                        } else if (WSDLConstants.QNAME_IMPORT.equals(name)) {
                            String importedDoc = reader.getAttributeValue(null, "location");
                            if (importedDoc != null) {
                                importedDocs.add(new URL(src.getSystemId(), importedDoc));
                            }
                        } else if (SCHEMA_INCLUDE_QNAME.equals(name) || SCHEMA_IMPORT_QNAME.equals(name)) {
                            String importedDoc = reader.getAttributeValue(null, "schemaLocation");
                            if (importedDoc != null) {
                                importedDocs.add(new URL(src.getSystemId(), importedDoc));
                            }
                        }
                    }
                    return new WSDLImpl(
                        rootName,systemId,src,tns,hasPortType,hasService,importedDocs);
                } else {
                    return new SDDocumentImpl(rootName,systemId,src);
                }
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new ServerRtException("runtime.parser.wsdl", systemId,e);
        } catch (XMLStreamException e) {
            throw new ServerRtException("runtime.parser.wsdl", systemId,e);
        }
    }


    private final QName rootName;
    private final SDDocumentSource source;

    /**
     * Set when {@link ServiceDefinitionImpl} is constructed.
     */
    /*package*/ ServiceDefinitionImpl owner;

    /**
     * The original system ID of this document.
     *
     * When this document contains relative references to other resources,
     * this field is used to find which {@link com.sun.xml.ws.server.SDDocumentImpl} it refers to.
     *
     * Must not be null.
     */
    private final URL url;
    private final Set<URL> imports;

    protected SDDocumentImpl(QName rootName, URL url, SDDocumentSource source) {
        this(rootName, url, source, new HashSet<URL>());
    }

    protected SDDocumentImpl(QName rootName, URL url, SDDocumentSource source, Set<URL> imports) {
        assert url!=null;
        this.rootName = rootName;
        this.source = source;
        this.url = url;
        this.imports = imports;
    }

    public QName getRootName() {
        return rootName;
    }

    public boolean isWSDL() {
        return false;
    }

    public boolean isSchema() {
        return false;
    }

    public URL getURL() {
        return url;
    }

    public XMLStreamReader read(XMLInputFactory xif) throws IOException, XMLStreamException {
        return source.read(xif);
    }

    public URL getSystemId() {
        return url;
    }

    public Set<URL> getImports() {
        return imports;
    }

    public void writeTo(PortAddressResolver portAddressResolver, DocumentAddressResolver resolver, OutputStream os) throws IOException {
        try {
            //generate the WSDL with utf-8 encoding and XML version 1.0
            XMLStreamWriter w = xof.createXMLStreamWriter(os, "utf-8");
            w.writeStartDocument("utf-8", "1.0");
            writeTo(portAddressResolver,resolver,w);
            w.writeEndDocument();
            w.flush();
            //w.close();
        } catch (XMLStreamException e) {
            e.printStackTrace();
            IOException ioe = new IOException(e.getMessage());
            ioe.initCause(e);
            throw ioe;
        }
    }

    public void writeTo(PortAddressResolver portAddressResolver, DocumentAddressResolver resolver, XMLStreamWriter out) throws XMLStreamException, IOException {
        for (SDDocumentFilter f : owner.filters) {
            out = f.filter(this,out);
        }

        new WSDLPatcher(owner.owner,this,portAddressResolver,resolver).bridge(
            source.read(xif), out );
    }


    /**
     * {@link SDDocument.Schema} implementation.
     *
     * @author Kohsuke Kawaguchi
     */
    private static final class SchemaImpl extends SDDocumentImpl implements SDDocument.Schema {
        private final String targetNamespace;

        public SchemaImpl(QName rootName, URL url, SDDocumentSource source, String targetNamespace,
                          Set<URL> imports) {
            super(rootName, url, source, imports);
            this.targetNamespace = targetNamespace;
        }

        public String getTargetNamespace() {
            return targetNamespace;
        }

        public boolean isSchema() {
            return true;
        }
    }


    private static final class WSDLImpl extends SDDocumentImpl implements SDDocument.WSDL {
        private final String targetNamespace;
        private final boolean hasPortType;
        private final boolean hasService;

        public WSDLImpl(QName rootName, URL url, SDDocumentSource source, String targetNamespace, boolean hasPortType,
                        boolean hasService, Set<URL> imports) {
            super(rootName, url, source, imports);
            this.targetNamespace = targetNamespace;
            this.hasPortType = hasPortType;
            this.hasService = hasService;
        }

        public String getTargetNamespace() {
            return targetNamespace;
        }

        public boolean hasPortType() {
            return hasPortType;
        }

        public boolean hasService() {
            return hasService;
        }

        public boolean isWSDL() {
            return true;
        }
    }

    private static final XMLInputFactory xif = XMLInputFactory.newInstance();
    private static final XMLOutputFactory xof = XMLOutputFactory.newInstance();
}
