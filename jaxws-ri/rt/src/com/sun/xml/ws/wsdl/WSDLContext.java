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
package com.sun.xml.ws.wsdl;

import com.sun.xml.ws.api.EndpointAddress;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.api.model.wsdl.WSDLService;
import com.sun.xml.ws.api.model.wsdl.WSDLModel;
import com.sun.xml.ws.api.wsdl.parser.WSDLParserExtension;
import com.sun.xml.ws.model.wsdl.WSDLModelImpl;
import com.sun.xml.ws.model.wsdl.WSDLPortImpl;
import com.sun.xml.ws.model.wsdl.WSDLServiceImpl;
import com.sun.xml.ws.util.ServiceConfigurationError;
import com.sun.xml.ws.util.ServiceFinder;
import com.sun.xml.ws.wsdl.parser.RuntimeWSDLParser;
import com.sun.xml.ws.wsdl.parser.XMLEntityResolver;
import static com.sun.xml.ws.streaming.XMLStreamReaderFactory.createXMLStreamReader;
import com.sun.xml.ws.streaming.XMLReader;
import com.sun.org.apache.xerces.internal.util.EntityResolverWrapper;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.WebServiceException;
import javax.xml.transform.Source;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Set;

/**
 * $author: JAXWS Development Team
 */
public class WSDLContext {
    private final URL orgWsdlLocation;
    private String targetNamespace;
    private final WSDLModelImpl wsdlDoc;

    /**
     * Creates a {@link WSDLContext} by parsing the given wsdl file.
     */
    public WSDLContext(URL wsdlDocumentLocation, EntityResolver entityResolver) throws WebServiceException {
        //must get binding information
        assert entityResolver != null;

        if (wsdlDocumentLocation == null)
            throw new WebServiceException("No WSDL location Information present, error");

        orgWsdlLocation = wsdlDocumentLocation;
        try {
            wsdlDoc = RuntimeWSDLParser.parse(wsdlDocumentLocation, entityResolver,
                ServiceFinder.find(WSDLParserExtension.class).toArray());
        } catch (IOException e) {
            throw new WebServiceException(e);
        } catch (XMLStreamException e) {
            throw new WebServiceException(e);
        } catch (SAXException e) {
            throw new WebServiceException(e);
        } catch (ServiceConfigurationError e) {
            throw new WebServiceException(e);
        }
    }

    /**
        * Creates a {@link WSDLContext} by parsing the given wsdl file.
        */
       public WSDLContext(URL loc, Source source, EntityResolver entityResolver) throws WebServiceException {
           //must get binding information
           assert entityResolver != null;
           orgWsdlLocation = loc;

           try {
               wsdlDoc = RuntimeWSDLParser.parse(loc, entityResolver, ServiceFinder.find(WSDLParserExtension.class).toArray());
           } catch (Exception e) {
                throw new WebServiceException(e);
           }
       }


    public WSDLModelImpl getWSDLModel() {
        return wsdlDoc;
    }

    public URL getWsdlLocation() {
        return orgWsdlLocation;
    }

    public String getOrigURLPath() {
        return orgWsdlLocation.getPath();
    }

    public QName getServiceQName() {
        return wsdlDoc.getFirstServiceName();
    }

    public QName getServiceQName(QName serviceName) {
        if (wsdlDoc.getServices().containsKey(serviceName))
            return serviceName;
        throw new WebServiceException("Error supplied serviceQName is not correct.");
    }

    //just get the first one for now
    public EndpointAddress getEndpoint(QName serviceName) {
        if (serviceName == null)
            throw new WebServiceException("Service unknown, can not identify ports for an unknown Service.");
        WSDLService service = wsdlDoc.getService(serviceName);
        EndpointAddress endpoint = null;
        if (service != null) {
            WSDLPort port = service.getFirstPort();
            if (port!=null) {
                endpoint = port.getAddress();
            }
        }
        if (endpoint == null)
            throw new WebServiceException("Endpoint not found. Check WSDL file to verify endpoint was provided.");
        return endpoint;
    }

    //just get the first one for now
    public QName getPortName() {
        return wsdlDoc.getFirstPortName();
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public void setTargetNamespace(String tns) {
        targetNamespace = tns;
    }

    public Iterable<WSDLPortImpl> getPorts(QName serviceName){
        WSDLServiceImpl service = wsdlDoc.getService(serviceName);
        if (service != null) {
            return service.getPorts();
        } else {
            return Collections.emptyList();
        }
    }


    public boolean contains(QName serviceName, QName portName) {
        WSDLService service = wsdlDoc.getService(serviceName);
        if (service != null) {
            return service.get(portName)!=null;
        }
        return false;
    }

    public QName getFirstServiceName() {
        return wsdlDoc.getFirstServiceName();
    }

    public Set<QName> getAllServiceNames() {
        return wsdlDoc.getServices().keySet();
    }
}