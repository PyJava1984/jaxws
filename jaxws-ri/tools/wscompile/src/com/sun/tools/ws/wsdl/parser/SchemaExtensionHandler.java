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

import java.util.Map;

import org.w3c.dom.Element;

import com.sun.tools.ws.wsdl.document.schema.SchemaConstants;
import com.sun.tools.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.ws.wsdl.framework.TWSDLParserContextImpl;
import com.sun.tools.ws.util.xml.XmlUtil;

/**
 * The XML Schema extension handler for WSDL.
 *
 * @author WS Development Team
 */
public class SchemaExtensionHandler extends AbstractExtensionHandler {

    public SchemaExtensionHandler(Map<String, AbstractExtensionHandler> extensionHandlerMap) {
        super(extensionHandlerMap);
    }

    public String getNamespaceURI() {
        return Constants.NS_XSD;
    }

    public boolean doHandleExtension(
        TWSDLParserContextImpl context,
        TWSDLExtensible parent,
        Element e) {
        if (XmlUtil.matchesTagNS(e, SchemaConstants.QNAME_SCHEMA)) {
            SchemaParser parser = new SchemaParser();
            parent.addExtension(parser.parseSchema(context, e, null));
            return true;
        } else {
            return false;
        }
    }
}
