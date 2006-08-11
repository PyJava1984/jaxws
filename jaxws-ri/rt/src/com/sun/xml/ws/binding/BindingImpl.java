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

package com.sun.xml.ws.binding;

import com.sun.istack.NotNull;
import com.sun.xml.ws.api.BindingID;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.pipe.Codec;
import com.sun.xml.ws.client.HandlerConfiguration;

import javax.xml.ws.handler.Handler;
import java.util.Collections;
import java.util.List;

/**
 * Instances are created by the service, which then
 * sets the handler chain on the binding impl.
 *
 * <p>This class is made abstract as we dont see a situation when a BindingImpl has much meaning without binding id.
 * IOw, for a specific binding there will be a class extending BindingImpl, for example SOAPBindingImpl.
 *
 * <p>The spi Binding interface extends Binding.
 *
 * @author WS Development Team
 */
public abstract class BindingImpl implements WSBinding {

    private HandlerConfiguration handlerConfig;
    private final BindingID bindingId;
    private String[] features;

    protected BindingImpl(BindingID bindingId) {
        this.bindingId = bindingId;
        setHandlerConfig(createHandlerConfig(Collections.<Handler>emptyList()));
    }

    public @NotNull List<Handler> getHandlerChain() {
        return handlerConfig.getHandlerChain();
    }

    public HandlerConfiguration getHandlerConfig() {
        return handlerConfig;
    }


    /**
     * Sets the handlers on the binding and then
     * sorts the handlers in to logical and protocol handlers.
     * Creates a new HandlerConfiguration object and sets it on the BindingImpl.
     */
    public void setHandlerChain(List<Handler> chain) {
        setHandlerConfig(createHandlerConfig(chain));
    }

    public void setFeatures(String[] features) {
        this.features = features;
    }

    public String[] getFeatures() {
        return features;
    }

    /**
     * This is called when ever Binding.setHandlerChain() or SOAPBinding.setRoles()
     * is called.
     * This sorts out the Handlers into Logical and SOAP Handlers and
     * sets the HandlerConfiguration.
     */
    protected void setHandlerConfig(HandlerConfiguration handlerConfig) {
        this.handlerConfig = handlerConfig;
    }

    protected abstract HandlerConfiguration createHandlerConfig(List<Handler> handlerChain);

    public @NotNull BindingID getBindingId(){
        return bindingId;
    }

    public final SOAPVersion getSOAPVersion() {
        return bindingId.getSOAPVersion();
    }

    public final @NotNull Codec createCodec() {
        return bindingId.createEncoder(this);
    }

    public static BindingImpl create(@NotNull BindingID bindingId) {
        if(bindingId.equals(BindingID.XML_HTTP))
            return new HTTPBindingImpl();
        else
            return new SOAPBindingImpl(bindingId);
    }

    public static WSBinding getDefaultBinding() {
        return new SOAPBindingImpl(BindingID.SOAP11_HTTP);
    }

    public boolean isMTOMEnabled(){
        return false;//default
    }

    public void setMTOMEnabled(boolean value){}

    public boolean hasFeature(String feature) {
        for (String f : getFeatures()) {
            if (feature.equals(f))
                return true;
        }

        return false;
    }
}
