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

package com.sun.xml.ws.server;

import com.sun.istack.NotNull;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.pipe.TubeCloner;
import com.sun.xml.ws.api.pipe.helper.AbstractTubeImpl;
import com.sun.xml.ws.api.server.AsyncProviderCallback;
import com.sun.xml.ws.api.server.InstanceResolver;
import com.sun.xml.ws.api.server.Invoker;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.api.server.WSWebServiceContext;
import com.sun.xml.ws.server.provider.ProviderInvokerTube;
import com.sun.xml.ws.server.sei.SEIInvokerTube;
import org.w3c.dom.Element;

import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Principal;

/**
 * Base code for {@link ProviderInvokerTube} and {@link SEIInvokerTube}.
 *
 * <p>
 * This hides {@link InstanceResolver} and performs a set up
 * necessary for {@link WebServiceContext} to correctly.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class InvokerTube<T> extends AbstractTubeImpl {

    private final Invoker invoker;
    private WSEndpoint endpoint;

    protected InvokerTube(Invoker invoker) {
        this.invoker = invoker;
    }

    public void setEndpoint(WSEndpoint endpoint) {
        this.endpoint = endpoint;
        invoker.start(webServiceContext,endpoint);
    }

    protected WSEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * Returns the application object that serves the request.
     *
    public final @NotNull T getServant(Packet request) {
        // this allows WebServiceContext to find this packet
        packets.set(request);
        return invoker.resolve(request);
    }
     */

    /**
     * Returns the {@link Invoker} object that serves the request.
     */
    public final @NotNull Invoker getInvoker(Packet request) {
        return wrapper;
    }

    /**
     * processRequest() and processResponse() do not share any instance variables
     * while processing the request. {@link InvokerTube} is stateless and terminal,
     * so no need to create copies.
     */
    public final AbstractTubeImpl copy(TubeCloner cloner) {
        cloner.add(this,this);
        return this;
    }

    public void preDestroy() {
        invoker.dispose();
    }

    /**
     * Heart of {@link WebServiceContext}.
     * Remembers which thread is serving which packet.
     */
    private static final ThreadLocal<Packet> packets = new ThreadLocal<Packet>();

    /**
     * This method can be called while the user service is servicing the request
     * synchronously, to obtain the current request packet.
     *
     * <p>
     * This is primarily designed for {@link StatefulInstanceResolver}. Use with care.
     */
    public static Packet getCurrentPacket() {
        return packets.get();
    }

    /**
     * {@link Invoker} filter that sets and restores the current packet.
     */
    private final Invoker wrapper = new Invoker() {
        @Override
        public Object invoke(Packet p, Method m, Object... args) throws InvocationTargetException, IllegalAccessException {
            Packet old = set(p);
            try {
                return invoker.invoke(p, m, args);
            } finally {
                set(old);
            }
        }

        @Override
        public <T>T invokeProvider(Packet p, T arg) throws IllegalAccessException, InvocationTargetException {
            Packet old = set(p);
            try {
                return invoker.invokeProvider(p, arg);
            } finally {
                set(old);
            }
        }

        @Override
        public <T>void invokeAsyncProvider(Packet p, T arg, AsyncProviderCallback cbak, WebServiceContext ctxt) throws IllegalAccessException, InvocationTargetException {
            Packet old = set(p);
            try {
                invoker.invokeAsyncProvider(p, arg, cbak, ctxt);
            } finally {
                set(old);
            }
        }

        private Packet set(Packet p) {
            Packet old = packets.get();
            packets.set(p);
            return old;
        }
    };

    /**
     * The single {@link WebServiceContext} instance injected into application.
     */
    private final WSWebServiceContext webServiceContext = new WSWebServiceContext() {

        public MessageContext getMessageContext() {
            return new EndpointMessageContextImpl(getRequestPacket());
        }

        public Principal getUserPrincipal() {
            Packet packet = getRequestPacket();
            return packet.webServiceContextDelegate.getUserPrincipal(packet);
        }

        public @NotNull Packet getRequestPacket() {
            Packet p = packets.get();
            assert p!=null; // invoker must set
            return p;
        }

        public boolean isUserInRole(String role) {
            Packet packet = getRequestPacket();
            return packet.webServiceContextDelegate.isUserInRole(packet,role);
        }

        public EndpointReference getEndpointReference(Element...referenceParameters) {
            return getEndpointReference(W3CEndpointReference.class, referenceParameters);
        }

        public <T extends EndpointReference> T getEndpointReference(Class<T> clazz, Element...referenceParameters) {
            Packet packet = getRequestPacket();
            String address = packet.webServiceContextDelegate.getEPRAddress(packet, endpoint);
            return clazz.cast(((WSEndpointImpl)endpoint).getEndpointReference(clazz,address,referenceParameters));
        }
    };
}
