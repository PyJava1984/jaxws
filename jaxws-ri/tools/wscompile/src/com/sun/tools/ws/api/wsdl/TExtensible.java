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

package com.sun.tools.ws.api.wsdl;


import javax.xml.namespace.QName;

/**
 * A WSDL element or attribute that can be extended.
 *
 * @author Vivek Pandey
 */
public interface TExtensible {
    /**
     * Gives the wsdl extensiblity element's name attribute value;
     */
    String getNameValue();

    /**
     * Gives namespace URI of a wsdl extensibility element.
     */
    String getNamespaceURI();

    /**
     * Gives the WSDL element or WSDL extensibility element name
     */
    QName getWSDLElementName();

    /**
     * An {@link TExtensionHandler} will call this method to add an {@link TExtension} object
     *
     * @param e non-null extension object
     */
    void addExtension(TExtension e);

    /**
     * Gives iterator over {@link TExtension}s
     */
    Iterable<? extends TExtension> extensions();

    /**
     * Gives the parent of a wsdl extensibility element.
     * <pre>
     * For example,
     *
     *     <wsdl:portType>
     *         <wsdl:operation>
     *     ...
     * Here, the {@link TExtensible}representing wsdl:operation's parent would be wsdl:portType
     *
     * @return null if the {@link TExtensible} has no parent, root of wsdl document - wsdl:definition.
     */
    TExtensible getParent();
}
