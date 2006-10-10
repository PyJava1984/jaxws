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

package com.sun.xml.ws.wsdl.parser;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.api.BindingID;
import com.sun.xml.ws.api.EndpointAddress;
import com.sun.xml.ws.api.model.ParameterBinding;
import com.sun.xml.ws.api.model.wsdl.WSDLDescriptorKind;
import com.sun.xml.ws.api.model.wsdl.WSDLModel;
import com.sun.xml.ws.api.wsdl.parser.MetaDataResolver;
import com.sun.xml.ws.api.wsdl.parser.MetadataResolverFactory;
import com.sun.xml.ws.api.wsdl.parser.ServiceDescriptor;
import com.sun.xml.ws.api.wsdl.parser.WSDLParserExtension;
import com.sun.xml.ws.model.wsdl.WSDLBoundOperationImpl;
import com.sun.xml.ws.model.wsdl.WSDLBoundPortTypeImpl;
import com.sun.xml.ws.model.wsdl.WSDLFaultImpl;
import com.sun.xml.ws.model.wsdl.WSDLInputImpl;
import com.sun.xml.ws.model.wsdl.WSDLMessageImpl;
import com.sun.xml.ws.model.wsdl.WSDLModelImpl;
import com.sun.xml.ws.model.wsdl.WSDLOperationImpl;
import com.sun.xml.ws.model.wsdl.WSDLOutputImpl;
import com.sun.xml.ws.model.wsdl.WSDLPartDescriptorImpl;
import com.sun.xml.ws.model.wsdl.WSDLPartImpl;
import com.sun.xml.ws.model.wsdl.WSDLPortImpl;
import com.sun.xml.ws.model.wsdl.WSDLPortTypeImpl;
import com.sun.xml.ws.model.wsdl.WSDLServiceImpl;
import com.sun.xml.ws.resources.ClientMessages;
import com.sun.xml.ws.streaming.SourceReaderFactory;
import com.sun.xml.ws.streaming.TidyXMLStreamReader;
import com.sun.xml.ws.streaming.XMLStreamReaderFactory;
import com.sun.xml.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.ws.util.ServiceFinder;
import com.sun.xml.ws.util.xml.XmlUtil;
import com.sun.xml.ws.wsdl.parser.XMLEntityResolver.Parser;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXException;

import javax.jws.soap.SOAPBinding.Style;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parses WSDL and builds {@link WSDLModel}.
 *
 * @author Vivek Pandey
 */
public class RuntimeWSDLParser {

    private final static BitSet errors = new BitSet();
    private static final int NOT_A_WSDL = 0;
    private final WSDLModelImpl wsdlDoc;
    /**
     * Target namespace URI of the WSDL that we are currently parsing.
     */
    private String targetNamespace;
    /**
     * System IDs of WSDLs that are already read.
     */
    private final Set<String> importedWSDLs = new HashSet<String>();
    /**
     * Must not be null.
     */
    private final XMLEntityResolver resolver;
    /**
     * The {@link WSDLParserExtension}. Always non-null.
     */
    private final WSDLParserExtension extensionFacade;

    private final WSDLParserExtensionContextImpl context;

    List<WSDLParserExtension> extensions;

    /**
     * Parses the WSDL and gives WSDLModel. If wsdl parameter is null, then wsdlLoc is used to get the WSDL. If the WSDL
     * document could not be obtained then {@link MetadataResolverFactory} is tried to get the WSDL document, if not found
     * then as last option, if the wsdlLoc has no '?wsdl' as query parameter then it is tried by appending '?wsdl'.
     */
    public static WSDLModelImpl parse(@NotNull URL wsdlLoc, @Nullable Source wsdl, @NotNull EntityResolver resolver, boolean isClientSide, WSDLParserExtension... extensions) throws IOException, XMLStreamException, SAXException {
        assert resolver != null;
        errors.clear();
        RuntimeWSDLParser parser = new RuntimeWSDLParser(wsdlLoc,new EntityResolverWrapper(resolver), isClientSide, extensions);
        WebServiceException wsdlException = null;
        try {
            if (wsdl == null)
                parser.parseWSDL(wsdlLoc);
            else
                parser.parseWSDL(wsdlLoc, wsdl);
        } catch (WebServiceException e) {
            wsdlException = e;
        }

        // Check to see if the obtained WSDL was a WSDL document. If not then try running with MEX else append with "?wsdl" if it doesn't end with one
        if (errors.get(NOT_A_WSDL)) {
            //try MEX
            MetaDataResolver mdResolver;
            ServiceDescriptor serviceDescriptor = null;

            //Currently we try the first available MetadataResolverFactory that gives us a WSDL document
            for (MetadataResolverFactory resolverFactory : ServiceFinder.find(MetadataResolverFactory.class)) {
                mdResolver = resolverFactory.metadataResolver(resolver);
                try {
                    serviceDescriptor = mdResolver.resolve(wsdlLoc.toURI());
                    //we got the ServiceDescriptor, now break
                    if (serviceDescriptor != null)
                        break;
                } catch (URISyntaxException e) {
                    throw new WebServiceException(e);
                }
            }
            if (serviceDescriptor != null) {
                List<? extends Source> wsdls = serviceDescriptor.getWSDLs();
                for (Source src : wsdls) {
                    try {
                        errors.clear(NOT_A_WSDL);
                        parser.parseWSDL(wsdlLoc, src);
                    } catch (WebServiceException e) {
                        wsdlException = e;
                    }
                }

            }
            //Incase that mex is not present or it couldn't get the metadata, try by appending ?wsdl and give
            // it a last shot else fail
            if (errors.get(NOT_A_WSDL) && wsdlLoc.getProtocol().equals("http") && (wsdlLoc.getQuery() == null)) {
                String urlString = wsdlLoc.toExternalForm();
                urlString += "?wsdl";
                wsdlLoc = new URL(urlString);

                //clear the NOT_A_WSDL error bit
                errors.clear(NOT_A_WSDL);
                try {
                    parser.parseWSDL(wsdlLoc);
                } catch (WebServiceException e) {
                    wsdlException = e;
                }
            }
        }
        //currently we fail only if we dont find a WSDL
        if (errors.get(NOT_A_WSDL) && wsdlException != null)
            throw wsdlException;

        parser.wsdlDoc.freeze();
        parser.extensionFacade.finished(parser.context);
        parser.extensionFacade.postFinished(parser.context);
        return parser.wsdlDoc;
    }

    public static WSDLModelImpl parse(URL wsdlLoc, EntityResolver resolver, boolean isClientSide, WSDLParserExtension... extensions) throws IOException, XMLStreamException, SAXException {
        return parse(wsdlLoc, null, resolver, isClientSide, extensions);
    }

    public static WSDLModelImpl parse(Parser wsdl, XMLEntityResolver resolver, boolean isClientSide, WSDLParserExtension... extensions) throws IOException, XMLStreamException, SAXException {
        assert resolver != null;
        RuntimeWSDLParser parser = new RuntimeWSDLParser( wsdl.systemId, resolver, isClientSide, extensions);
        parser.parseWSDL(wsdl);
        parser.wsdlDoc.freeze();
        parser.extensionFacade.finished(parser.context);
        parser.extensionFacade.postFinished(parser.context);
        return parser.wsdlDoc;
    }

    private RuntimeWSDLParser(URL sourceLocation, XMLEntityResolver resolver, boolean isClientSide, WSDLParserExtension... extensions) {
        this.wsdlDoc = new WSDLModelImpl(sourceLocation);
        this.resolver = resolver;

        this.extensions = new ArrayList<WSDLParserExtension>();
        this.context = new WSDLParserExtensionContextImpl(wsdlDoc, isClientSide);

        // register handlers for default extensions
        register(new MemberSubmissionAddressingWSDLParserExtension());
        register(new W3CAddressingWSDLParserExtension());

        for (WSDLParserExtension e : extensions)
            register(e);

        this.extensionFacade = new WSDLParserExtensionFacade(this.extensions.toArray(new WSDLParserExtension[0]));
    }

    private void parseWSDL(URL wsdlLoc) throws XMLStreamException, IOException, SAXException {

        String systemId = wsdlLoc.toExternalForm();

        XMLEntityResolver.Parser parser = resolver.resolveEntity(null, systemId);
        if (parser == null) {
            parser = new Parser(wsdlLoc, createReader(wsdlLoc));
        }
        parseWSDL(parser);
    }

    private void parseWSDL(URL url, Source wsdlLoc) throws XMLStreamException, IOException, SAXException {
        XMLStreamReader reader = createReader(wsdlLoc);
        importedWSDLs.clear();
        Parser parser = new Parser(url, reader);
        parseWSDL(parser);
    }


    private void parseWSDL(Parser parser) throws XMLStreamException, IOException, SAXException {
        // avoid processing the same WSDL twice.
        if (!importedWSDLs.add(parser.systemId.toExternalForm()))
            return;


        XMLStreamReader reader = parser.parser;
        XMLStreamReaderUtil.nextElementContent(reader);

        //wsdl:definition
        if (!reader.getName().equals(WSDLConstants.QNAME_DEFINITIONS)) {
            errors.set(NOT_A_WSDL);
            throw new WebServiceException(ClientMessages.RUNTIME_WSDLPARSER_INVALID_WSDL(parser.systemId.toExternalForm(),
                    WSDLConstants.QNAME_DEFINITIONS.toString(), reader.getName().toString(), reader.getLocation()));
        }

        //get the targetNamespace of the service
        String tns = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_TNS);

        final String oldTargetNamespace = targetNamespace;
        targetNamespace = tns;

        while (XMLStreamReaderUtil.nextElementContent(reader) !=
                XMLStreamConstants.END_ELEMENT) {
            if (reader.getEventType() == XMLStreamConstants.END_DOCUMENT)
                break;

            QName name = reader.getName();
            if (WSDLConstants.QNAME_IMPORT.equals(name)) {
                parseImport(parser.systemId, reader);
            } else if (WSDLConstants.QNAME_MESSAGE.equals(name)) {
                parseMessage(reader);
            } else if (WSDLConstants.QNAME_PORT_TYPE.equals(name)) {
                parsePortType(reader);
            } else if (WSDLConstants.QNAME_BINDING.equals(name)) {
                parseBinding(reader);
            } else if (WSDLConstants.QNAME_SERVICE.equals(name)) {
                parseService(reader);
            } else {
                extensionFacade.definitionsElements(reader);
            }
        }
        targetNamespace = oldTargetNamespace;
        reader.close();
    }

    private void parseService(XMLStreamReader reader) {
        String serviceName = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
        WSDLServiceImpl service = new WSDLServiceImpl(reader,wsdlDoc,new QName(targetNamespace, serviceName));
        extensionFacade.serviceAttributes(service, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (WSDLConstants.QNAME_PORT.equals(name)) {
                parsePort(reader, service);
                if (reader.getEventType() != XMLStreamConstants.END_ELEMENT) {
                    XMLStreamReaderUtil.next(reader);
                }
            } else {
                extensionFacade.serviceElements(service, reader);
            }
        }
        wsdlDoc.addService(service);
    }

    private void parsePort(XMLStreamReader reader, WSDLServiceImpl service) {
        String portName = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
        String binding = ParserUtil.getMandatoryNonEmptyAttribute(reader, "binding");

        QName bindingName = ParserUtil.getQName(reader, binding);
        QName portQName = new QName(service.getName().getNamespaceURI(), portName);
        WSDLPortImpl port = new WSDLPortImpl(reader,service, portQName, bindingName);

        extensionFacade.portAttributes(port, reader);

        String location = null;
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (SOAPConstants.QNAME_ADDRESS.equals(name) || SOAPConstants.QNAME_SOAP12ADDRESS.equals(name)) {
                location = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_LOCATION);
                XMLStreamReaderUtil.next(reader);
            } else {
                extensionFacade.portElements(port, reader);
            }
        }
        if (location != null) {
            try {
                port.setAddress(new EndpointAddress(location));
            } catch (URISyntaxException e) {
                //TODO:i18nify
                throw new WebServiceException("Malformed URL: " + location, e);
            }
        }
        service.put(portQName, port);
    }

    private void parseBinding(XMLStreamReader reader) {
        String bindingName = ParserUtil.getMandatoryNonEmptyAttribute(reader, "name");
        String portTypeName = ParserUtil.getMandatoryNonEmptyAttribute(reader, "type");
        if ((bindingName == null) || (portTypeName == null)) {
            //TODO: throw exception?
            //
            //  wsdl:binding element for now
            XMLStreamReaderUtil.skipElement(reader);
            return;
        }
        WSDLBoundPortTypeImpl binding = new WSDLBoundPortTypeImpl(reader,wsdlDoc, new QName(targetNamespace, bindingName),
                ParserUtil.getQName(reader, portTypeName));
        extensionFacade.bindingAttributes(binding, reader);

        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (WSDLConstants.NS_SOAP_BINDING.equals(name)) {
                binding.setBindingId(BindingID.SOAP11_HTTP);
                String style = reader.getAttributeValue(null, "style");

                if ((style != null) && (style.equals("rpc"))) {
                    binding.setStyle(Style.RPC);
                } else {
                    binding.setStyle(Style.DOCUMENT);
                }
                XMLStreamReaderUtil.next(reader);
            } else if (WSDLConstants.NS_SOAP12_BINDING.equals(name)) {
                binding.setBindingId(BindingID.SOAP12_HTTP);
                String style = reader.getAttributeValue(null, "style");
                if ((style != null) && (style.equals("rpc"))) {
                    binding.setStyle(Style.RPC);
                } else {
                    binding.setStyle(Style.DOCUMENT);
                }
                XMLStreamReaderUtil.next(reader);
            } else if (WSDLConstants.QNAME_OPERATION.equals(name)) {
                parseBindingOperation(reader, binding);
            } else {
                extensionFacade.bindingElements(binding, reader);
            }
        }
    }


    private void parseBindingOperation(XMLStreamReader reader, WSDLBoundPortTypeImpl binding) {
        String bindingOpName = ParserUtil.getMandatoryNonEmptyAttribute(reader, "name");
        if (bindingOpName == null) {
            //TODO: throw exception?
            //skip wsdl:binding element for now
            XMLStreamReaderUtil.skipElement(reader);
            return;
        }

        QName opName = new QName(binding.getPortTypeName().getNamespaceURI(), bindingOpName);
        WSDLBoundOperationImpl bindingOp = new WSDLBoundOperationImpl(reader,binding, opName);
        binding.put(opName, bindingOp);
        extensionFacade.bindingOperationAttributes(bindingOp, reader);

        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (WSDLConstants.QNAME_INPUT.equals(name)) {
                parseInputBinding(reader, bindingOp);
            } else if (WSDLConstants.QNAME_OUTPUT.equals(name)) {
                parseOutputBinding(reader, bindingOp);
            } else if (WSDLConstants.QNAME_FAULT.equals(name)) {
                parseFaultBinding(reader, bindingOp);
            } else if (SOAPConstants.QNAME_OPERATION.equals(name) ||
                    SOAPConstants.QNAME_SOAP12OPERATION.equals(name)) {
                String style = reader.getAttributeValue(null, "style");
                /**
                 *  If style attribute is present set it otherwise set the style as defined
                 *  on the <soap:binding> element
                 */
                if (style != null) {
                    if (style.equals("rpc"))
                        bindingOp.setStyle(Style.RPC);
                    else
                        bindingOp.setStyle(Style.DOCUMENT);
                } else {
                    bindingOp.setStyle(binding.getStyle());
                }
                String soapAction = reader.getAttributeValue(null, "soapAction");

                if (soapAction != null)
                    bindingOp.setSoapAction(soapAction);

                XMLStreamReaderUtil.next(reader);
            } else {
                extensionFacade.bindingOperationElements(bindingOp, reader);
            }
        }
    }

    private void parseInputBinding(XMLStreamReader reader, WSDLBoundOperationImpl bindingOp) {
        boolean bodyFound = false;
        extensionFacade.bindingOperationInputAttributes(bindingOp, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if ((SOAPConstants.QNAME_BODY.equals(name) || SOAPConstants.QNAME_SOAP12BODY.equals(name)) && !bodyFound) {
                bodyFound = true;
                bindingOp.setInputExplicitBodyParts(parseSOAPBodyBinding(reader, bindingOp, BindingMode.INPUT));
                goToEnd(reader);
            } else if ((SOAPConstants.QNAME_HEADER.equals(name) || SOAPConstants.QNAME_SOAP12HEADER.equals(name))) {
                parseSOAPHeaderBinding(reader, bindingOp.getInputParts());
            } else if (MIMEConstants.QNAME_MULTIPART_RELATED.equals(name)) {
                parseMimeMultipartBinding(reader, bindingOp, BindingMode.INPUT);
            } else {
                extensionFacade.bindingOperationInputElements(bindingOp, reader);
            }
        }
    }

    private void parseOutputBinding(XMLStreamReader reader, WSDLBoundOperationImpl bindingOp) {
        boolean bodyFound = false;
        extensionFacade.bindingOperationOutputAttributes(bindingOp, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if ((SOAPConstants.QNAME_BODY.equals(name) || SOAPConstants.QNAME_SOAP12BODY.equals(name)) && !bodyFound) {
                bodyFound = true;
                bindingOp.setOutputExplicitBodyParts(parseSOAPBodyBinding(reader, bindingOp, BindingMode.OUTPUT));
                goToEnd(reader);
            } else if ((SOAPConstants.QNAME_HEADER.equals(name) || SOAPConstants.QNAME_SOAP12HEADER.equals(name))) {
                parseSOAPHeaderBinding(reader, bindingOp.getOutputParts());
            } else if (MIMEConstants.QNAME_MULTIPART_RELATED.equals(name)) {
                parseMimeMultipartBinding(reader, bindingOp, BindingMode.OUTPUT);
            } else {
                extensionFacade.bindingOperationOutputElements(bindingOp, reader);
            }
        }
    }

    private void parseFaultBinding(XMLStreamReader reader, WSDLBoundOperationImpl bindingOp) {
        boolean bodyFound = false;
        extensionFacade.bindingOperationFaultAttributes(bindingOp, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if ((SOAPConstants.QNAME_BODY.equals(name) || SOAPConstants.QNAME_SOAP12BODY.equals(name)) && !bodyFound) {
                bodyFound = true;
                bindingOp.setFaultExplicitBodyParts(parseSOAPBodyBinding(reader, bindingOp.getFaultParts()));
                goToEnd(reader);
            } else if ((SOAPConstants.QNAME_HEADER.equals(name) || SOAPConstants.QNAME_SOAP12HEADER.equals(name))) {
                parseSOAPHeaderBinding(reader, bindingOp.getFaultParts());
            } else if (MIMEConstants.QNAME_MULTIPART_RELATED.equals(name)) {
                parseMimeMultipartBinding(reader, bindingOp, BindingMode.FAULT);
            } else {
                extensionFacade.bindingOperationFaultElements(bindingOp, reader);
            }
        }
    }

    private enum BindingMode {
        INPUT, OUTPUT, FAULT}

    private static boolean parseSOAPBodyBinding(XMLStreamReader reader, WSDLBoundOperationImpl op, BindingMode mode) {
        String namespace = reader.getAttributeValue(null, "namespace");
        if (mode == BindingMode.INPUT) {
            op.setRequestNamespace(namespace);
            return parseSOAPBodyBinding(reader, op.getInputParts());
        }
        //resp
        op.setResponseNamespace(namespace);
        return parseSOAPBodyBinding(reader, op.getOutputParts());
    }

    /**
     * @param reader
     * @param parts
     * @return Returns true if body has explicit parts declaration
     */
    private static boolean parseSOAPBodyBinding(XMLStreamReader reader, Map<String, ParameterBinding> parts) {
        String partsString = reader.getAttributeValue(null, "parts");
        if (partsString != null) {
            List<String> partsList = XmlUtil.parseTokenList(partsString);
            if (partsList.isEmpty()) {
                parts.put(" ", ParameterBinding.BODY);
            } else {
                for (String part : partsList) {
                    parts.put(part, ParameterBinding.BODY);
                }
            }
            return true;
        }
        return false;
    }

    private static void parseSOAPHeaderBinding(XMLStreamReader reader, Map<String, ParameterBinding> parts) {
        String part = reader.getAttributeValue(null, "part");
        //if(part == null| part.equals("")||message == null || message.equals("")){
        if (part == null || part.equals("")) {
            return;
        }

        //lets not worry about message attribute for now, probably additional headers wont be there
        //String message = reader.getAttributeValue(null, "message");
        //QName msgName = ParserUtil.getQName(reader, message);
        parts.put(part, ParameterBinding.HEADER);
        goToEnd(reader);
    }


    private static void parseMimeMultipartBinding(XMLStreamReader reader, WSDLBoundOperationImpl op, BindingMode mode) {
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (MIMEConstants.QNAME_PART.equals(name)) {
                parseMIMEPart(reader, op, mode);
            } else {
                XMLStreamReaderUtil.skipElement(reader);
            }
        }
    }

    private static void parseMIMEPart(XMLStreamReader reader, WSDLBoundOperationImpl op, BindingMode mode) {
        boolean bodyFound = false;
        Map<String, ParameterBinding> parts = null;
        if (mode == BindingMode.INPUT) {
            parts = op.getInputParts();
        } else if (mode == BindingMode.OUTPUT) {
            parts = op.getOutputParts();
        } else if (mode == BindingMode.FAULT) {
            parts = op.getFaultParts();
        }
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (SOAPConstants.QNAME_BODY.equals(name) && !bodyFound) {
                bodyFound = true;
                parseSOAPBodyBinding(reader, op, mode);
                XMLStreamReaderUtil.next(reader);
            } else if (SOAPConstants.QNAME_HEADER.equals(name)) {
                bodyFound = true;
                parseSOAPHeaderBinding(reader, parts);
                XMLStreamReaderUtil.next(reader);
            } else if (MIMEConstants.QNAME_CONTENT.equals(name)) {
                String part = reader.getAttributeValue(null, "part");
                String type = reader.getAttributeValue(null, "type");
                if ((part == null) || (type == null)) {
                    XMLStreamReaderUtil.skipElement(reader);
                    continue;
                }
                ParameterBinding sb = ParameterBinding.createAttachment(type);
                if (parts != null && sb != null && part != null)
                    parts.put(part, sb);
                XMLStreamReaderUtil.next(reader);
            } else {
                XMLStreamReaderUtil.skipElement(reader);
            }
        }
    }

    protected void parseImport(URL baseURL, XMLStreamReader reader) throws IOException, SAXException, XMLStreamException {
        // expand to the absolute URL of the imported WSDL.
        String importLocation =
                ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_LOCATION);
        URL importURL = new URL(baseURL, importLocation);
        parseWSDL(importURL);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            XMLStreamReaderUtil.skipElement(reader);
        }
    }

    private void parsePortType(XMLStreamReader reader) {
        String portTypeName = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
        if (portTypeName == null) {
            //TODO: throw exception?
            //skip wsdl:portType element for now
            XMLStreamReaderUtil.skipElement(reader);
            return;
        }
        WSDLPortTypeImpl portType = new WSDLPortTypeImpl(reader,wsdlDoc, new QName(targetNamespace, portTypeName));
        extensionFacade.portTypeAttributes(portType, reader);
        wsdlDoc.addPortType(portType);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (WSDLConstants.QNAME_OPERATION.equals(name)) {
                parsePortTypeOperation(reader, portType);
            } else {
                extensionFacade.portTypeElements(portType, reader);
            }
        }
    }


    private void parsePortTypeOperation(XMLStreamReader reader, WSDLPortTypeImpl portType) {
        String operationName = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
        if (operationName == null) {
            //TODO: throw exception?
            //skip wsdl:portType element for now
            XMLStreamReaderUtil.skipElement(reader);
            return;
        }

        QName operationQName = new QName(portType.getName().getNamespaceURI(), operationName);
        WSDLOperationImpl operation = new WSDLOperationImpl(reader,portType, operationQName);
        extensionFacade.portTypeOperationAttributes(operation, reader);
        String parameterOrder = ParserUtil.getAttribute(reader, "parameterOrder");
        operation.setParameterOrder(parameterOrder);
        portType.put(operationName, operation);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (name.equals(WSDLConstants.QNAME_INPUT)) {
                parsePortTypeOperationInput(reader, operation);
            } else if (name.equals(WSDLConstants.QNAME_OUTPUT)) {
                parsePortTypeOperationOutput(reader, operation);
            } else if (name.equals(WSDLConstants.QNAME_FAULT)) {
                parsePortTypeOperationFault(reader, operation);
            } else {
                extensionFacade.portTypeOperationElements(operation, reader);
            }
        }
    }


    private void parsePortTypeOperationFault(XMLStreamReader reader, WSDLOperationImpl operation) {
        String msg = ParserUtil.getMandatoryNonEmptyAttribute(reader, "message");
        QName msgName = ParserUtil.getQName(reader, msg);
        String name = ParserUtil.getMandatoryNonEmptyAttribute(reader, "name");
        WSDLFaultImpl fault = new WSDLFaultImpl(reader,name, msgName);
        operation.addFault(fault);
        extensionFacade.portTypeOperationFaultAttributes(fault, reader);
        extensionFacade.portTypeOperationFault(operation, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            extensionFacade.portTypeOperationFaultElements(fault, reader);
        }
    }

    private void parsePortTypeOperationInput(XMLStreamReader reader, WSDLOperationImpl operation) {
        String msg = ParserUtil.getMandatoryNonEmptyAttribute(reader, "message");
        QName msgName = ParserUtil.getQName(reader, msg);
        String name = ParserUtil.getAttribute(reader, "name");
        WSDLInputImpl input = new WSDLInputImpl(reader, name, msgName, operation);
        operation.setInput(input);
        extensionFacade.portTypeOperationInputAttributes(input, reader);
        extensionFacade.portTypeOperationInput(operation, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            extensionFacade.portTypeOperationInputElements(input, reader);
        }
    }

    private void parsePortTypeOperationOutput(XMLStreamReader reader, WSDLOperationImpl operation) {
        String msg = ParserUtil.getAttribute(reader, "message");
        QName msgName = ParserUtil.getQName(reader, msg);
        String name = ParserUtil.getAttribute(reader, "name");
        WSDLOutputImpl output = new WSDLOutputImpl(reader,name, msgName, operation);
        operation.setOutput(output);
        extensionFacade.portTypeOperationOutputAttributes(output, reader);
        extensionFacade.portTypeOperationOutput(operation, reader);
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            extensionFacade.portTypeOperationOutputElements(output, reader);
        }
    }

    private void parseMessage(XMLStreamReader reader) {
        String msgName = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
        WSDLMessageImpl msg = new WSDLMessageImpl(reader,new QName(targetNamespace, msgName));
        extensionFacade.messageAttributes(msg, reader);
        int partIndex = 0;
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            QName name = reader.getName();
            if (WSDLConstants.QNAME_PART.equals(name)) {
                String part = ParserUtil.getMandatoryNonEmptyAttribute(reader, WSDLConstants.ATTR_NAME);
                String desc = null;
                int index = reader.getAttributeCount();
                WSDLDescriptorKind kind = WSDLDescriptorKind.ELEMENT;
                for (int i = 0; i < index; i++) {
                    QName descName = reader.getAttributeName(i);
                    if (descName.getLocalPart().equals("element"))
                        kind = WSDLDescriptorKind.ELEMENT;
                    else if (descName.getLocalPart().equals("TYPE"))
                        kind = WSDLDescriptorKind.TYPE;

                    if (descName.getLocalPart().equals("element") || descName.getLocalPart().equals("type")) {
                        desc = reader.getAttributeValue(i);
                        break;
                    }
                }
                if (desc == null)
                    continue;

                WSDLPartImpl wsdlPart = new WSDLPartImpl(reader, part, partIndex, new WSDLPartDescriptorImpl(reader,ParserUtil.getQName(reader, desc), kind));
                msg.add(wsdlPart);
                if (reader.getEventType() != XMLStreamConstants.END_ELEMENT)
                    goToEnd(reader);
            } else {
                extensionFacade.messageElements(msg, reader);
            }
        }
        wsdlDoc.addMessage(msg);
        if (reader.getEventType() != XMLStreamConstants.END_ELEMENT)
            goToEnd(reader);
    }

    private static void goToEnd(XMLStreamReader reader) {
        while (XMLStreamReaderUtil.nextElementContent(reader) != XMLStreamConstants.END_ELEMENT) {
            XMLStreamReaderUtil.skipElement(reader);
        }
    }

    /**
     * Make sure to return a "fresh" reader each time it is called because
     * more than one active reader may be needed within a single thread
     * to parse a WSDL file.
     */
    private XMLStreamReader createReader(URL wsdlLoc) throws IOException {
        InputStream stream = wsdlLoc.openStream();
        return new TidyXMLStreamReader(XMLStreamReaderFactory.createFreshXMLStreamReader(wsdlLoc.toExternalForm(), stream), stream);
    }

    private XMLStreamReader createReader(Source src) {
        return new TidyXMLStreamReader(SourceReaderFactory.createSourceReader(src, true), null);
    }

    private void register(WSDLParserExtension e) {
        extensions.add(e);
    }
}
