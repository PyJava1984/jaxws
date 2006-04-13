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

package com.sun.xml.ws.transport.http;

import com.sun.istack.NotNull;
import com.sun.xml.ws.api.BindingID;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.server.Container;
import com.sun.xml.ws.api.server.InstanceResolver;
import com.sun.xml.ws.api.server.SDDocumentSource;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.binding.BindingImpl;
import com.sun.xml.ws.handler.HandlerChainsModel;
import com.sun.xml.ws.resources.WsservletMessages;
import com.sun.xml.ws.server.EndpointFactory;
import com.sun.xml.ws.server.ServerRtException;
import com.sun.xml.ws.streaming.Attributes;
import com.sun.xml.ws.streaming.XMLStreamReaderFactory;
import com.sun.xml.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.ws.util.HandlerAnnotationInfo;
import com.sun.xml.ws.util.xml.XmlUtil;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;

import javax.jws.WebService;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.WebServiceProvider;
import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.soap.SOAPBinding;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parses {@code sun-jaxws.xml} into {@link WSEndpoint}.
 *
 * <p>
 * Since {@code sun-jaxws.xml} captures more information that what {@link WSEndpoint}
 * represents (in particular URL pattern and name), this class
 * takes a parameterization 'A' so that the user of this parser can choose to
 * create another type that wraps {@link WSEndpoint}.
 *
 * {@link HttpAdapter} and its derived type is used for this often,
 * but it can be anything.
 *
 * @author WS Development Team
 * @author Kohsuke Kawaguchi
 */
public class DeploymentDescriptorParser<A> {
    private final Container container;
    private final ClassLoader classLoader;
    private final ResourceLoader loader;
    private final AdapterFactory<A> adapterFactory;

    /**
     * Endpoint names that are declared.
     * Used to catch double definitions.
     */
    private final Set<String> names = new HashSet<String>();

    /**
     * WSDL/schema documents collected from /WEB-INF/wsdl. Keyed by the system ID.
     */
    private final Map<String,SDDocumentSource> docs = new HashMap<String,SDDocumentSource>();

    /**
     *
     * @param cl
     *      Used to load service implementations.
     * @param loader
     *      Used to locate resources, in particular WSDL.
     * @param container
     *      Optional {@link Container} that {@link WSEndpoint}s receive.
     * @param adapterFactory
     *      Creates {@link HttpAdapter} (or its derived class.)
     */
    public DeploymentDescriptorParser(ClassLoader cl, ResourceLoader loader, Container container, AdapterFactory<A> adapterFactory) throws MalformedURLException {
        classLoader = cl;
        this.loader = loader;
        this.container = container;
        this.adapterFactory = adapterFactory;

        collectDocs("/WEB-INF/wsdl/");
        logger.fine("war metadata="+docs);
    }

    /**
     * Parses the {@code sun-jaxws.xml} file and configures
     * a set of {@link HttpAdapter}s.
     *
     * @return
     *      can be empty but non-null.
     */
    public List<A> parse(InputStream is) {
        try {
            XMLStreamReader reader =
                XMLStreamReaderFactory.createFreshXMLStreamReader(new InputSource(is), true);
            XMLStreamReaderUtil.nextElementContent(reader);
            return parseAdapters(reader);
        } catch (XMLStreamException e) {
            throw new ServerRtException("runtime.parser.xmlReader",e);
        }
    }

    /**
     * Get all the WSDL & schema documents recursively.
     */
    private void collectDocs(String dirPath) throws MalformedURLException {
        Set<String> paths = loader.getResourcePaths(dirPath);
        if (paths != null) {
            for (String path : paths) {
                if (path.endsWith("/")) {
                    collectDocs(path);
                } else {
                    URL res = loader.getResource(path);
                    docs.put(res.toString(),SDDocumentSource.create(res));
                }
            }
        }
    }


    private List<A> parseAdapters(XMLStreamReader reader) throws XMLStreamException {
        if (!reader.getName().equals(QNAME_ENDPOINTS)) {
            failWithFullName("runtime.parser.invalidElement", reader);
        }

        List<A> adapters = new ArrayList<A>();

        Attributes attrs = XMLStreamReaderUtil.getAttributes(reader);
        String version = getMandatoryNonEmptyAttribute(reader, attrs, ATTR_VERSION);
        if (!version.equals(ATTRVALUE_VERSION_1_0)) {
            failWithLocalName("runtime.parser.invalidVersionNumber",
                reader, version);
        }

        while (XMLStreamReaderUtil.nextElementContent(reader) !=
            XMLStreamConstants.END_ELEMENT) {
            if (reader.getName().equals(QNAME_ENDPOINT)) {

                attrs = XMLStreamReaderUtil.getAttributes(reader);
                String name = getMandatoryNonEmptyAttribute(reader, attrs, ATTR_NAME);
                if (!names.add(name)) {
                    logger.warning(
                        WsservletMessages.SERVLET_WARNING_DUPLICATE_ENDPOINT_NAME(/*name*/));
                }

                String implementationName =
                    getMandatoryNonEmptyAttribute(reader, attrs, ATTR_IMPLEMENTATION);
                Class implementorClass = getImplementorClass(implementationName);
                verifyImplementorClass(implementorClass);

                SDDocumentSource primaryWSDL = getPrimaryWSDL(attrs, implementorClass);

                QName serviceName = getQNameAttribute(attrs, ATTR_SERVICE);
                if(serviceName == null)
                    serviceName = EndpointFactory.getDefaultServiceName(implementorClass);

                QName portName = getQNameAttribute(attrs, ATTR_PORT);
                if(portName == null)
                    portName = EndpointFactory.getDefaultPortName(serviceName,implementorClass);

                //get enable-mtom attribute value
                String mtom = getAttribute(attrs, ATTR_ENABLE_MTOM);

                BindingID bindingId;
                {//set Binding using DD, annotation, or default one(in that order)
                    String attr = getAttribute(attrs, ATTR_BINDING);
                    if(attr!=null) {
                        // Convert short-form tokens to API's binding ids
                        attr = getBindingIdForToken(attr);
                        bindingId = BindingID.parse(attr);
                        // enable-mtom attribute should override the default mtom setting. Which can happen if hte binding
                        // ID in DD doesn't explicitly sets MTOM
                        if(bindingId.getMtomSetting().isDefault()){
                            bindingId.getMtomSetting().enable(Boolean.valueOf(mtom));
                        }
                    } else{
                        bindingId = BindingID.parse(implementorClass);
                        //enable-mtom attribute should override mtom setting from annotation
                        if(mtom != null){
                            bindingId.getMtomSetting().enable(Boolean.valueOf(mtom));
                        }
                    }
                }

                String mtomThreshold = getAttribute(attrs, ATTR_MTOM_THRESHOLD_VALUE);
                if(mtomThreshold != null){
                    int mtomThresholdValue = Integer.valueOf(mtomThreshold);
                    //rei.setMtomThreshold(mtomThresholdValue);
                    // TODO: still haven't figured out how to do this correctly
                    // with the new code
                }


                WSBinding binding = BindingImpl.create(bindingId);
                String urlPattern =
                    getMandatoryNonEmptyAttribute(reader, attrs, ATTR_URL_PATTERN);

                // TODO use 'docs' as the metadata. If wsdl is non-null it's the primary.

                boolean handlersSetInDD = setHandlersAndRoles(binding, reader, serviceName, portName);
                
                ensureNoContent(reader);
                WSEndpoint<?> endpoint = WSEndpoint.create(
                    implementorClass,!handlersSetInDD,
                    InstanceResolver.createSingleton(getImplementor(implementorClass)),
                    serviceName, portName, container, binding,
                    primaryWSDL, docs.values(), createEntityResolver()
                    );
                adapters.add(adapterFactory.createAdapter(name, urlPattern, endpoint));
            } else {
                failWithLocalName("runtime.parser.invalidElement", reader);
            }
        }

        reader.close();

        return adapters;
    }

    /**
     * JSR-109 defines short-form tokens for standard binding Ids. These are
     * used only in DD. So stand alone deployment descirptor should also honor
     * these tokens. This method converts the tokens to API's standard
     * binding ids
     *
     * @param lexical binding attribute value from DD. Always not null
     *
     * @return returns corresponding API's binding ID or the same lexical
     */
    public static @NotNull String getBindingIdForToken(@NotNull String lexical) {
        if (lexical.equals("##SOAP11_HTTP")) {
            return SOAPBinding.SOAP11HTTP_BINDING;
        } else if (lexical.equals("##SOAP11_HTTP_MTOM")) {
            return SOAPBinding.SOAP11HTTP_MTOM_BINDING;
        } else if (lexical.equals("##SOAP12_HTTP")) {
            return SOAPBinding.SOAP12HTTP_BINDING;
        } else if (lexical.equals("##SOAP12_HTTP_MTOM")) {
            return SOAPBinding.SOAP12HTTP_MTOM_BINDING;
        } else if (lexical.equals("##XML_HTTP")) {
            return HTTPBinding.HTTP_BINDING;
        }
        return lexical;
    }

    /**
     * Creates a new "Adapter".
     *
     * <P>
     * Normally 'A' would be {@link HttpAdapter} or some derived class.
     * But the parser doesn't require that to be of any particular type.
     */
    public static interface AdapterFactory<A> {
        A createAdapter(String name, String urlPattern, WSEndpoint<?> endpoint);
    }

    /**
     * Verifies if the endpoint implementor class has @WebService or @WebServiceProvider
     * annotation
     * @throws java.lang.IllegalArgumentException
     *      If it doesn't have any one of @WebService or @WebServiceProvider
     *      If it has both @WebService and @WebServiceProvider annotations
     *
     */
    private void verifyImplementorClass(Class<?> implementorClass) {
        WebServiceProvider wsProvider = implementorClass.getAnnotation(WebServiceProvider.class);
        WebService ws = implementorClass.getAnnotation(WebService.class);
        if (wsProvider == null && ws == null) {
            throw new IllegalArgumentException(implementorClass+" has neither @WebSerivce nor @WebServiceProvider annotation");
        }
        if (wsProvider != null && ws != null) {
            throw new IllegalArgumentException(implementorClass+" has both @WebSerivce and @WebServiceProvider annotations");
        }
    }

    /**
     * Checks the deployment descriptor or {@link @WebServiceProvider} annotation
     * to see if it points to any WSDL. If so, returns the {@link SDDocumentSource}.
     *
     * @return
     *      The pointed WSDL, if any. Otherwise null.
     */
    private SDDocumentSource getPrimaryWSDL(Attributes attrs, Class<?> implementorClass) {
        {
            String wsdlFile = getAttribute(attrs, ATTR_WSDL);
            if (wsdlFile == null) {
                WebServiceProvider wsProvider = implementorClass.getAnnotation(WebServiceProvider.class);
                if (wsProvider != null && !wsProvider.wsdlLocation().equals("")) {
                    wsdlFile = wsProvider.wsdlLocation();
                }
            }

            if(wsdlFile!=null) {
                if (!wsdlFile.startsWith(JAXWS_WSDL_DD_DIR)) {
                    logger.warning("Ignoring wrong wsdl="+wsdlFile+". It should start with "
                            +JAXWS_WSDL_DD_DIR
                            +". Going to generate and publish a new WSDL.");
                    wsdlFile = null;
                }

                try {
                    URL wsdl = loader.getResource('/'+wsdlFile);
                    SDDocumentSource docInfo = docs.get(wsdl.toExternalForm());
                    if(docInfo==null) {
                        // this shouldn't happen since 'docs' should contain
                        // all the WSDLs inside /WEB-INF/wsdl, and wsdlFile
                        // is required to be in this directory.
                        logger.log(Level.WARNING,"Ignoring wrong wsdl="+wsdlFile);
                    }
                    return docInfo;
                } catch (MalformedURLException e) {
                    logger.log(Level.WARNING,"Ignoring wrong wsdl="+wsdlFile,e);
                }
            }
        }

        return null;
    }

    /**
     * Creates an {@link EntityResolver} that consults {@code /WEB-INF/jax-ws-catalog.xml}.
     */
    private EntityResolver createEntityResolver() {
        try {
            return XmlUtil.createEntityResolver(loader.getCatalogFile());
        } catch(MalformedURLException e) {
            throw new WebServiceException(e);
        }
    }

    protected String getAttribute(Attributes attrs, String name) {
        String value = attrs.getValue(name);
        if (value != null) {
            value = value.trim();
        }
        return value;
    }

    protected QName getQNameAttribute(Attributes attrs, String name) {
        String value = getAttribute(attrs, name);
        if (value == null || value.equals("")) {
            return null;
        } else {
            return QName.valueOf(value);
        }
    }

    protected String getNonEmptyAttribute(XMLStreamReader reader, Attributes attrs, String name) {
        String value = getAttribute(attrs, name);
        if (value != null && value.equals("")) {
            failWithLocalName(
                "runtime.parser.invalidAttributeValue",
                reader,
                name);
        }
        return value;
    }

    protected String getMandatoryAttribute(XMLStreamReader reader, Attributes attrs, String name) {
        String value = getAttribute(attrs, name);
        if (value == null) {
            failWithLocalName("runtime.parser.missing.attribute", reader, name);
        }
        return value;
    }

    protected String getMandatoryNonEmptyAttribute(XMLStreamReader reader, Attributes attributes,
                                                   String name) {
        String value = getAttribute(attributes, name);
        if (value == null) {
            failWithLocalName("runtime.parser.missing.attribute", reader, name);
        } else if (value.equals("")) {
            failWithLocalName(
                "runtime.parser.invalidAttributeValue",
                reader,
                name);
        }
        return value;
    }

    /**
     * Parses the handler and role information and sets it
     * on the {@link WSBinding}.
     * @return true if <handler-chains> element present in DD
     *         false otherwise.
     */
    protected boolean setHandlersAndRoles(WSBinding binding, XMLStreamReader reader, QName serviceName, QName portName) {

        if (XMLStreamReaderUtil.nextElementContent(reader) ==
            XMLStreamConstants.END_ELEMENT ||
            !reader.getName().equals(
            HandlerChainsModel.QNAME_HANDLER_CHAINS)) {

            return false;
        }

        HandlerAnnotationInfo handlerInfo = HandlerChainsModel.parseHandlerFile(
            reader, classLoader,serviceName, portName, binding);

        binding.setHandlerChain(handlerInfo.getHandlers());
        if (binding instanceof SOAPBinding) {
            ((SOAPBinding)binding).setRoles(handlerInfo.getRoles());
        }

        // move past </handler-chains>
        XMLStreamReaderUtil.nextContent(reader);
        return true;
    }

    protected static void ensureNoContent(XMLStreamReader reader) {
        if (reader.getEventType() != XMLStreamConstants.END_ELEMENT) {
            fail("runtime.parser.unexpectedContent", reader);
        }
    }

    protected static void fail(String key, XMLStreamReader reader) {
        logger.log(Level.SEVERE, key + reader.getLocation().getLineNumber());
        throw new ServerRtException(
            key,
            Integer.toString(reader.getLocation().getLineNumber()));
    }

    protected static void failWithFullName(String key, XMLStreamReader reader) {
        throw new ServerRtException(
            key,
            reader.getLocation().getLineNumber(),
            reader.getName());
    }

    protected static void failWithLocalName(String key, XMLStreamReader reader) {
        throw new ServerRtException(
            key,
            reader.getLocation().getLineNumber(),
            reader.getLocalName());
    }

    protected static void failWithLocalName(
        String key,
        XMLStreamReader reader,
        String arg) {
        throw new ServerRtException(
            key,
            reader.getLocation().getLineNumber(),
            reader.getLocalName(),
            arg);
    }

    protected Class loadClass(String name) {
        try {
            return Class.forName(name, true, classLoader);
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new ServerRtException(
                "runtime.parser.classNotFound",
                name);
        }
    }

    /*
    * Gets endpoint implementation class
    */
    protected Class getImplementorClass(String name) {
        try {
            return Class.forName(name, true, classLoader);
        } catch (ClassNotFoundException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new ServerRtException(
                "runtime.parser.classNotFound", name);
        }
    }

    /*
     * Instantiates endpoint implementation
     */
    protected Object getImplementor(Class cl) {
        try {
            return cl.newInstance();
        } catch (InstantiationException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new ServerRtException(
                "error.implementorFactory.newInstanceFailed", cl.getName());
        } catch (IllegalAccessException e) {
            logger.log(Level.SEVERE, e.getMessage(), e);
            throw new ServerRtException(
                "error.implementorFactory.newInstanceFailed", cl.getName());
        }
    }

    public static final String NS_RUNTIME =
        "http://java.sun.com/xml/ns/jax-ws/ri/runtime";

    public static final String JAXWS_WSDL_DD_DIR = "WEB-INF/wsdl";

    public static final QName QNAME_ENDPOINTS =
        new QName(NS_RUNTIME, "endpoints");
    public static final QName QNAME_ENDPOINT =
        new QName(NS_RUNTIME, "endpoint");

    public static final String ATTR_VERSION = "version";
    public static final String ATTR_NAME = "name";
    public static final String ATTR_IMPLEMENTATION = "implementation";
    public static final String ATTR_WSDL = "wsdl";
    public static final String ATTR_SERVICE = "service";
    public static final String ATTR_PORT = "port";
    public static final String ATTR_URL_PATTERN = "url-pattern";
    public static final String ATTR_ENABLE_MTOM = "enable-mtom";
    public static final String ATTR_MTOM_THRESHOLD_VALUE = "mtom-threshold-value";
    public static final String ATTR_BINDING = "binding";

    public static final String ATTRVALUE_VERSION_1_0 = "2.0";
    private static final Logger logger =
        Logger.getLogger(
            com.sun.xml.ws.util.Constants.LoggingDomain + ".server.http");
}
