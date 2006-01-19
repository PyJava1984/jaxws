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
package com.sun.xml.ws.model.wsdl;

import com.sun.xml.ws.api.model.Mode;
import com.sun.xml.ws.api.model.ParameterBinding;
import com.sun.xml.ws.api.model.wsdl.PortType;
import com.sun.xml.ws.api.model.wsdl.WSDLBinding;
import com.sun.xml.ws.api.model.wsdl.BindingOperation;
import com.sun.xml.ws.api.model.wsdl.WSDLExtension;

import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;

public class WSDLBindingImpl extends HashMap<String, BindingOperation> implements WSDLBinding {
    private QName name;
    private QName portTypeName;
    private PortType portType;
    private String bindingId;
    private WSDLModelImpl wsdlDoc;
    private boolean finalized = false;
    private Set<WSDLExtension> extensions;

    public WSDLBindingImpl(QName name, QName portTypeName) {
        super();
        this.name = name;
        this.portTypeName = portTypeName;
        extensions = new HashSet<WSDLExtension>();
    }

    public QName getName() {
        return name;
    }

    public QName getPortTypeName(){
        return portTypeName;
    }

    public PortType getPortType() {
        return portType;
    }

    public void setPortType(PortType portType) {
        this.portType = portType;
    }

    public String getBindingId() {
        return bindingId;
    }

    public void setBindingId(String bindingId) {
        this.bindingId = bindingId;
    }

    public void setWsdlDocument(WSDLModelImpl wsdlDoc) {
        this.wsdlDoc = wsdlDoc;
    }

    public ParameterBinding getBinding(String operation, String part, Mode mode){
        BindingOperation op = get(operation);
        if(op == null){
            //TODO throw exception
            return null;
        }
        if((Mode.IN == mode)||(Mode.INOUT == mode))
            return op.getInputBinding(part);
        else
            return op.getOutputBinding(part);
    }

    public String getMimeType(String operation, String part, Mode mode){
        BindingOperation op = get(operation);
        if(Mode.IN == mode)
            return op.getMimeTypeForInputPart(part);
        else
            return op.getMimeTypeForOutputPart(part);
    }

    /**
     * This method is called to apply binings in case when a specific port is required
     */
    public void finalizeBinding(){
        if(!finalized){
            wsdlDoc.finalizeBinding(this);
            finalized = true;
        }
    }

    public Iterator<WSDLExtension> getWSDLExtensions() {
        return extensions.iterator();
    }

    public void addWSDLExtension(WSDLExtension ext){
        extensions.add(ext);
    }
}
