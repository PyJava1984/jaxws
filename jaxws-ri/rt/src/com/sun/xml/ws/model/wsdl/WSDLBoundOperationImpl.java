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
package com.sun.xml.ws.model.wsdl;

import com.sun.xml.ws.api.model.ParameterBinding;
import com.sun.xml.ws.api.model.wsdl.*;
import com.sun.istack.Nullable;

import javax.jws.soap.SOAPBinding.Style;
import javax.jws.WebParam.Mode;
import javax.xml.namespace.QName;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of {@link WSDLBoundOperation}
 *
 * @author Vivek Pandey
 */
public final class WSDLBoundOperationImpl extends AbstractExtensibleImpl implements WSDLBoundOperation {
    private final QName name;

    // map of wsdl:part to the binding
    private final Map<String, ParameterBinding> inputParts;
    private final Map<String, ParameterBinding> outputParts;
    private final Map<String, String> inputMimeTypes;
    private final Map<String, String> outputMimeTypes;

    private boolean explicitInputSOAPBodyParts = false;
    private boolean explicitOutputSOAPBodyParts = false;

    private Boolean emptyInputBody;
    private Boolean emptyOutputBody;

    private final Map<String, WSDLPartImpl> inParts;
    private final Map<String, WSDLPartImpl> outParts;
    private WSDLOperationImpl operation;

    private final WSDLBoundPortTypeImpl owner;

    /**
     *
     * @param name wsdl:operation name qualified value
     */
    public WSDLBoundOperationImpl(WSDLBoundPortTypeImpl owner, QName name) {
        this.name = name;
        inputParts = new HashMap<String, ParameterBinding>();
        outputParts = new HashMap<String, ParameterBinding>();
        inputMimeTypes = new HashMap<String, String>();
        outputMimeTypes = new HashMap<String, String>();
        inParts = new HashMap<String, WSDLPartImpl>();
        outParts = new HashMap<String, WSDLPartImpl>();
        this.owner = owner;
    }

    public QName getName(){
        return name;
    }

    /**
     * Gets {@link com.sun.xml.ws.api.model.wsdl.WSDLPart} for the given wsdl:input or wsdl:output part
     *
     * @param partName must be non-null
     * @param mode     must be non-null
     * @return null if no part is found
     */
    public WSDLPartImpl getPart(String partName, Mode mode){
        if(mode==Mode.IN){
            return inParts.get(partName);
        }else if(mode==Mode.OUT){
            return outParts.get(partName);
        }
        return null;
    }

    public void addPart(WSDLPartImpl part, Mode mode){
        if(mode==Mode.IN)
            inParts.put(part.getName(), part);
        else if(mode==Mode.OUT)
            outParts.put(part.getName(), part);
    }

    /**
     * Map of wsdl:input part name and the binding as {@link ParameterBinding}
     *
     * @return empty Map if there is no parts
     */
    public Map<String, ParameterBinding> getInputParts() {
        return inputParts;
    }

    /**
     * Map of wsdl:output part name and the binding as {@link ParameterBinding}
     *
     * @return empty Map if there is no parts
     */
    public Map<String, ParameterBinding> getOutputParts() {
        return outputParts;
    }

    /**
     * Map of mime:content@part and the mime type from mime:content@type for wsdl:output
     *
     * @return empty Map if there is no parts
     */
    public Map<String, String> getInputMimeTypes() {
        return inputMimeTypes;
    }

    /**
     * Map of mime:content@part and the mime type from mime:content@type for wsdl:output
     *
     * @return empty Map if there is no parts
     */
    public Map<String, String> getOutputMimeTypes() {
        return outputMimeTypes;
    }

    /**
     * Gets {@link ParameterBinding} for a given wsdl part in wsdl:input
     *
     * @param part Name of wsdl:part, must be non-null
     * @return null if the part is not found.
     */
    public ParameterBinding getInputBinding(String part){
        if(emptyInputBody == null){
            if(inputParts.get(" ") != null)
                emptyInputBody = true;
            else
                emptyInputBody = false;
        }
        ParameterBinding block = inputParts.get(part);
        if(block == null){
            if(explicitInputSOAPBodyParts || emptyInputBody)
                return ParameterBinding.UNBOUND;
            return ParameterBinding.BODY;
        }

        return block;
    }

    /**
     * Gets {@link ParameterBinding} for a given wsdl part in wsdl:output
     *
     * @param part Name of wsdl:part, must be non-null
     * @return null if the part is not found.
     */
    public ParameterBinding getOutputBinding(String part){
        if(emptyOutputBody == null){
            if(outputParts.get(" ") != null)
                emptyOutputBody = true;
            else
                emptyOutputBody = false;
        }
        ParameterBinding block = outputParts.get(part);
        if(block == null){
            if(explicitOutputSOAPBodyParts || emptyOutputBody)
                return ParameterBinding.UNBOUND;
            return ParameterBinding.BODY;
        }

        return block;
    }

    /**
     * Gets the MIME type for a given wsdl part in wsdl:input
     *
     * @param part Name of wsdl:part, must be non-null
     * @return null if the part is not found.
     */
    public String getMimeTypeForInputPart(String part){
        return inputMimeTypes.get(part);
    }

    /**
     * Gets the MIME type for a given wsdl part in wsdl:output
     *
     * @param part Name of wsdl:part, must be non-null
     * @return null if the part is not found.
     */
    public String getMimeTypeForOutputPart(String part){
        return outputMimeTypes.get(part);
    }

    public WSDLOperation getOperation() {
        return operation;
    }

    public void setInputExplicitBodyParts(boolean b) {
        explicitInputSOAPBodyParts = b;
    }

    public void setOutputExplicitBodyParts(boolean b) {
        explicitOutputSOAPBodyParts = b;
    }

    private Style style = Style.DOCUMENT;
    public void setStyle(Style style){
        this.style = style;
    }

    public @Nullable QName getPayloadName() {
        if(style.equals(Style.RPC)){
            return name;
        }else{
            if(emptyPayload)
                return null;
            
            if(payloadName != null)
                return payloadName;

            QName inMsgName = operation.getInput().getMessage().getName();
            WSDLMessageImpl message = messages.get(inMsgName);
            for(WSDLPartImpl part:message.parts()){
                ParameterBinding binding = getInputBinding(part.getName());
                if(binding.isBody()){
                    payloadName = part.getDescriptor().name();
                    return payloadName;
                }
            }

            //Its empty payload
            emptyPayload = true;
        }
        //empty body
        return null;
    }

    WSDLBoundPortTypeImpl getOwner(){
        return owner;
    }

    private QName payloadName;
    private boolean emptyPayload;
    private Map<QName, WSDLMessageImpl> messages;

    void freeze(WSDLModelImpl parent) {
        messages = parent.getMessages();
        operation = owner.getPortType().get(name.getLocalPart());
    }
}
