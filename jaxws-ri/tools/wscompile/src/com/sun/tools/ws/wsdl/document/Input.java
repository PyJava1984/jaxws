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

package com.sun.tools.ws.wsdl.document;

import com.sun.tools.ws.api.wsdl.TExtensible;
import com.sun.tools.ws.api.wsdl.TExtension;
import com.sun.tools.ws.wsdl.framework.ExtensibilityHelper;
import javax.xml.namespace.QName;

import com.sun.tools.ws.wsdl.framework.AbstractDocument;
import com.sun.tools.ws.wsdl.framework.Entity;
import com.sun.tools.ws.wsdl.framework.EntityReferenceAction;
import com.sun.tools.ws.wsdl.framework.QNameAction;

/**
 * Entity corresponding to the "input" child element of a port type operation.
 *
 * @author WS Development Team
 */
public class Input extends Entity implements TExtensible {

    public Input() {
        _helper = new ExtensibilityHelper();
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }

    public QName getMessage() {
        return _message;
    }

    public void setMessage(QName n) {
        _message = n;
    }

    public Message resolveMessage(AbstractDocument document) {
        return (Message) document.find(Kinds.MESSAGE, _message);
    }

    public QName getElementName() {
        return WSDLConstants.QNAME_INPUT;
    }

    public Documentation getDocumentation() {
        return _documentation;
    }

    public void setDocumentation(Documentation d) {
        _documentation = d;
    }

    public void withAllQNamesDo(QNameAction action) {
        if (_message != null) {
            action.perform(_message);
        }
    }

    public void withAllEntityReferencesDo(EntityReferenceAction action) {
        super.withAllEntityReferencesDo(action);
        if (_message != null) {
            action.perform(Kinds.MESSAGE, _message);
        }
    }

    public void accept(WSDLDocumentVisitor visitor) throws Exception {
        visitor.preVisit(this);
        visitor.postVisit(this);
    }

    public void validateThis() {
        if (_message == null) {
            failValidation("validation.missingRequiredAttribute", "message");
        }
    }

    private Documentation _documentation;
    private String _name;
    private QName _message;
    private ExtensibilityHelper _helper;
    private TExtensible parent;

    public void addExtension(TExtension e) {
        _helper.addExtension(e);
    }

    public QName getWSDLElementName() {
        return getElementName();
    }

    public TExtensible getParent() {
        return parent;
    }
    
    public void setParent(TExtensible parent) {
        this.parent = parent;
    }

    public String getNamespaceURI() {
        return getElementName().getNamespaceURI();
    }

    public String getNameValue() {
        return null;
    }

    public Iterable<? extends TExtension> extensions() {
        return _helper.extensions();
    }
}
