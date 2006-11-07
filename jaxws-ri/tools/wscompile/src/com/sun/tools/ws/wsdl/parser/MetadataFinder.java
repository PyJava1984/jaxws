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

package com.sun.tools.ws.wsdl.parser;

import com.sun.tools.ws.resources.WsdlMessages;
import com.sun.tools.ws.wscompile.ErrorReceiver;
import com.sun.tools.ws.wscompile.WsimportOptions;
import com.sun.tools.ws.wsdl.document.WSDLConstants;
import com.sun.tools.ws.wsdl.document.schema.SchemaConstants;
import com.sun.tools.ws.wsdl.framework.ParseException;
import com.sun.xml.ws.api.wsdl.parser.MetaDataResolver;
import com.sun.xml.ws.api.wsdl.parser.MetadataResolverFactory;
import com.sun.xml.ws.api.wsdl.parser.ServiceDescriptor;
import com.sun.xml.ws.util.DOMUtil;
import com.sun.xml.ws.util.ServiceFinder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.LocatorImpl;

import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * @author Vivek Pandey
 */
public final class MetadataFinder extends DOMForest{

    public MetadataFinder(InternalizationLogic logic, WsimportOptions options, ErrorReceiver errReceiver) {
        super(logic, options, errReceiver);

    }

    public void parseWSDL() throws SAXException {
        // parse source grammars
        for (InputSource value : options.getWSDLs()) {
            String systemID = value.getSystemId();
            errorReceiver.pollAbort();
            Document dom = parse(value, true);
            if (dom == null)
                continue;
            Element doc = dom.getDocumentElement();
            if (doc == null) {
                continue;
            }
            //if its not a WSDL document, retry with MEX
            if (doc.getNamespaceURI() == null || !doc.getNamespaceURI().equals(WSDLConstants.NS_WSDL) || !doc.getLocalName().equals("definitions")) {
                core.remove(systemID);
                rootDocuments.remove(systemID);
                errorReceiver.warning(locatorTable.getStartLocation(doc), WsdlMessages.INVALID_WSDL_WITH_DOOC(systemID, "{" + fixNull(doc.getNamespaceURI()) + "}" + doc.getLocalName()));
                dom = getFromMetadataResolver(systemID);
            }
            NodeList schemas = doc.getElementsByTagNameNS(SchemaConstants.NS_XSD, "schema");
            for (int i = 0; i < schemas.getLength(); i++) {
                if(!inlinedSchemaElements.contains(schemas.item(i)))
                    inlinedSchemaElements.add((Element) schemas.item(i));
            }
        }
    }

    private String fixNull(String s) {
        if (s == null) return "";
        else return s;
    }


    /*
    * If source and target namespace are also passed in,
    * then if the mex resolver is found and it cannot get
    * the data, wsimport attempts to add ?wsdl to the
    * address and retrieve the data with a normal http get.
    * This behavior should only happen when trying a
    * mex request first.
    */
    private Document getFromMetadataResolver(String systemId) {

        //try MEX
        MetaDataResolver resolver = null;
        ServiceDescriptor serviceDescriptor = null;
        for (MetadataResolverFactory resolverFactory : ServiceFinder.find(MetadataResolverFactory.class)) {
            resolver = resolverFactory.metadataResolver(options.entityResolver);
            try {
                serviceDescriptor = resolver.resolve(new URI(systemId));
                //we got the ServiceDescriptor, now break
                if (serviceDescriptor != null)
                    break;
            } catch (URISyntaxException e) {
                throw new ParseException(e);
            }
        }

        if (serviceDescriptor != null) {
            return parseMetadata(systemId, serviceDescriptor);
        } else {
            errorReceiver.error(new LocatorImpl(), WsdlMessages.PARSING_UNABLE_TO_GET_METADATA(systemId));
        }
        return null;
    }

    private Document parseMetadata(String systemId, ServiceDescriptor serviceDescriptor) {
        List<? extends Source> mexWsdls = serviceDescriptor.getWSDLs();
        List<? extends Source> mexSchemas = serviceDescriptor.getSchemas();
        Document root = null;
        for (Source src : mexWsdls) {
            if (src instanceof DOMSource) {
                Node n = ((DOMSource) src).getNode();
                Document doc;
                if (n.getNodeType() == Node.ELEMENT_NODE && n.getOwnerDocument() == null) {
                    doc = DOMUtil.createDom();
                    doc.importNode(n, true);
                } else {
                    doc = n.getOwnerDocument();
                }

//                Element e = (n.getNodeType() == Node.ELEMENT_NODE)?(Element)n: DOMUtil.getFirstElementChild(n);
                if (root == null) {
                    //check if its main wsdl, then set it to root
                    NodeList nl = doc.getDocumentElement().getElementsByTagNameNS(WSDLConstants.NS_WSDL, "service");
                    if (nl.getLength() > 0) {
                        root = doc;
                        mexRootDoc = src.getSystemId();;
                    }
                }
                NodeList nl = doc.getDocumentElement().getElementsByTagNameNS(WSDLConstants.NS_WSDL, "import");
                if (nl.getLength() > 0) {
                    Element imp = (Element) nl.item(0);
                    String loc = imp.getAttribute("location");
                    if (loc != null) {
                        if (!externalReferences.contains(loc))
                            externalReferences.add(loc);
                    }
                }
                if (core.keySet().contains(systemId))
                    core.remove(systemId);
                core.put(src.getSystemId(), doc);
                isMexMetadata = true;
            }

            //TODO:handle SAXSource
            //TODO:handler StreamSource
        }

        for (Source src : mexSchemas) {
            if (src instanceof DOMSource) {
                Node n = ((DOMSource) src).getNode();
                Element e = (n.getNodeType() == Node.ELEMENT_NODE) ? (Element) n : DOMUtil.getFirstElementChild(n);
                inlinedSchemaElements.add(e);
            }
            //TODO:handle SAXSource
            //TODO:handler StreamSource
        }
        return root;
    }

    public boolean isMexMetadata;
    private String mexRootDoc;

    public Document getMexRootWSDL() {
        return get(mexRootDoc);
    }


}
