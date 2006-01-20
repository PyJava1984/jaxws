/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */
package com.sun.xml.ws.wsdl;

import com.sun.xml.ws.api.model.wsdl.BoundPortType;
import com.sun.xml.ws.api.model.wsdl.Port;
import com.sun.xml.ws.api.model.wsdl.Service;
import com.sun.xml.ws.api.model.wsdl.WSDLModel;
import com.sun.xml.ws.model.wsdl.WSDLModelImpl;
import com.sun.xml.ws.wsdl.parser.RuntimeWSDLParser;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

/**
 * $author: JAXWS Development Team
 */
public class WSDLContext {
    private final URL orgWsdlLocation;
    private String targetNamespace;
    private String bindingId;
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
            wsdlDoc = (WSDLModelImpl) RuntimeWSDLParser.parse(wsdlDocumentLocation, entityResolver);
        } catch (IOException e) {
            throw new WebServiceException(e);
        } catch (XMLStreamException e) {
            throw new WebServiceException(e);
        } catch (SAXException e) {
            throw new WebServiceException(e);
        }

        String bId = wsdlDoc.getBindingId();
        if (bId != null)
            setBindingID(bId);
    }

    public WSDLModel getWSDLModel() {
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
    public String getEndpoint(QName serviceName) {
        if (serviceName == null)
            throw new WebServiceException("Service unknown, can not identify ports for an unknown Service.");
        Service service = wsdlDoc.getService(serviceName);
        String endpoint = null;
        if (service != null) {
            Iterator<Port> iter = service.getPorts().iterator();
            if (iter.hasNext()) {
                Port port = iter.next();
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

    public String getBindingID(QName serviceName, QName portName) {
        return getWsdlDocument().getBindingId(serviceName, portName);
    }

    public void setBindingID(String id) {
        bindingId = id;
    }

    public String getTargetNamespace() {
        return targetNamespace;
    }

    public void setTargetNamespace(String tns) {
        targetNamespace = tns;
    }

    public Iterable<Port> getPorts(QName serviceName){
        Service service = wsdlDoc.getService(serviceName);
        if (service != null) {
            return service.getPorts();
        } else {
            return Collections.emptyList();
        }
    }


    public boolean contains(QName serviceName, QName portName) {
        Service service = wsdlDoc.getService(serviceName);
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

    public WSDLModel getWsdlDocument() {
        return wsdlDoc;
    }

    public BoundPortType getWsdlBinding(QName service, QName port) {
        if (wsdlDoc == null)
            return null;
        return wsdlDoc.getBinding(service, port);
    }

    public String getEndpoint(QName serviceName, QName portQName) {
        Service service = wsdlDoc.getService(serviceName);
        if (service != null) {
            Port p = service.get(portQName);
            if (p != null)
                return p.getAddress();
            else
                throw new WebServiceException("No ports found for service " + serviceName);
        } else {
            throw new WebServiceException("Service unknown, can not identify ports for an unknown Service.");
        }
    }
}
