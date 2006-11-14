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

package com.sun.xml.ws.client;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.Closeable;
import com.sun.xml.ws.api.EndpointAddress;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.addressing.AddressingVersion;
import com.sun.xml.ws.api.addressing.WSEndpointReference;
import com.sun.xml.ws.api.message.Header;
import com.sun.xml.ws.api.message.HeaderList;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.api.pipe.Engine;
import com.sun.xml.ws.api.pipe.Fiber;
import com.sun.xml.ws.api.pipe.Tube;
import com.sun.xml.ws.binding.BindingImpl;
import com.sun.xml.ws.developer.JAXWSProperties;
import com.sun.xml.ws.developer.WSBindingProvider;
import com.sun.xml.ws.util.Pool;
import com.sun.xml.ws.util.Pool.TubePool;
import com.sun.xml.ws.util.RuntimeVersion;

import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.http.HTTPBinding;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Base class for stubs, which accept method invocations from
 * client applications and pass the message to a {@link Tube}
 * for processing.
 *
 * <p>
 * This class implements the management of pipe instances,
 * and most of the {@link BindingProvider} methods.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class Stub implements com.sun.xml.ws.api.client.WSBindingProvider, ResponseContextReceiver, Closeable {

    /**
     * Reuse pipelines as it's expensive to create.
     * <p>
     * Set to null when {@link #close() closed}.
     */
    private Pool<Tube> tubes;

    private final Engine engine;

    /**
     * The {@link WSServiceDelegate} object that owns us.
     */
    protected final WSServiceDelegate owner;

    /**
     * Non-null if this stub is configured to talk to an EPR.
     * <p>
     * When this field is non-null, its reference parameters are sent as out-bound headers.
     * This field can be null even when addressing is enabled, but if the addressing is
     * not enabled, this field must be null.
     * <p>
     * Unlike endpoint address, we are not letting users to change the EPR,
     * as it contains references to services and so on that we don't want to change.
     */
    protected final @Nullable WSEndpointReference endpointReference;

    protected final BindingImpl binding;

    public final RequestContext requestContext = new RequestContext();

    /**
     * {@link ResponseContext} from the last synchronous operation.
     */
    private ResponseContext responseContext;
    @Nullable protected final WSDLPort wsdlPort;

    /**
     * {@link Header}s to be added to outbound {@link Packet}.
     * The contents is determined by the user.
     */
    @Nullable private volatile Header[] userOutboundHeaders;

    /**
     * @param master                 The created stub will send messages to this pipe.
     * @param binding                As a {@link BindingProvider}, this object will
     *                               return this binding from {@link BindingProvider#getBinding()}.
     * @param defaultEndPointAddress The destination of the message. The actual destination
     *                               could be overridden by {@link RequestContext}.
     * @param epr                    To create a stub that sends out reference parameters
     *                               of a specific EPR, give that instance. Otherwise null.
     *                               Its address field will not be used, and that should be given
     *                               separately as the <tt>defaultEndPointAddress</tt>.
     */
    protected Stub(WSServiceDelegate owner, Tube master, BindingImpl binding, WSDLPort wsdlPort, EndpointAddress defaultEndPointAddress, @Nullable WSEndpointReference epr) {
        this.owner = owner;
        this.tubes = new TubePool(master);
        this.wsdlPort = wsdlPort;
        this.binding = binding;
        this.requestContext.setEndpointAddress(defaultEndPointAddress);
        this.engine = new Engine();
        this.endpointReference = epr;

        //Issue number Pending: Addressing does not need to be enabled to use an EPR
        //if(AddressingVersion.isEnabled(binding) && epr!=null)
        //    throw new WebServiceException(ClientMessages.EPR_WITHOUT_ADDRESSING_ON());
    }

    /**
     * Gets the port name that this stub is configured to talk to.
     * <p>
     * When {@link #wsdlPort} is non-null, the port name is always
     * the same as {@link WSDLPort#getName()}, but this method
     * returns a port name even if no WSDL is available for this stub.
     */
    protected abstract @NotNull QName getPortName();

    /**
     * Gets the service name that this stub is configured to talk to.
     * <p>
     * When {@link #wsdlPort} is non-null, the service name is always
     * the same as the one that's inferred from {@link WSDLPort#getOwner()},
     * but this method returns a port name even if no WSDL is available for
     * this stub.
     */
    protected final @NotNull QName getServiceName() {
        return owner.getServiceName();
    }

    /**
     * Gets the {@link Executor} to be used for asynchronous method invocations.
     * <p>
     * Note that the value this method returns may different from invocations
     * to invocations. The caller must not cache.
     *
     * @return always non-null.
     */
    public final Executor getExecutor() {
        return owner.getExecutor();
    }
    
    /**
     * Passes a message to a pipe for processing.
     * <p>
     * Unlike {@link Tube} instances,
     * this method is thread-safe and can be invoked from
     * multiple threads concurrently.
     *
     * @param packet         The message to be sent to the server
     * @param requestContext The {@link RequestContext} when this invocation is originally scheduled.
     *                       This must be the same object as {@link #requestContext} for synchronous
     *                       invocations, but for asynchronous invocations, it needs to be a snapshot
     *                       captured at the point of invocation, to correctly satisfy the spec requirement.
     * @param receiver       Receives the {@link ResponseContext}. Since the spec requires
     *                       that the asynchronous invocations must not update response context,
     *                       depending on the mode of invocation they have to go to different places.
     *                       So we take a setter that abstracts that away.
     */
    protected final Packet process(Packet packet, RequestContext requestContext, ResponseContextReceiver receiver) {
        {// fill in Packet
            packet.proxy = this;
            packet.handlerConfig = binding.getHandlerConfig();
            requestContext.fill(packet);
            if (AddressingVersion.isEnabled(binding)) {
                if(endpointReference!=null)
                    endpointReference.addReferenceParameters(packet.getMessage().getHeaders());
            }

            // to make it multi-thread safe we need to first get a stable snapshot
            Header[] hl = userOutboundHeaders;
            if(hl!=null)
                packet.getMessage().getHeaders().addAll(hl);
        }

        Packet reply;

        Pool<Tube> pool = tubes;
        if (pool == null)
            throw new WebServiceException("close method has already been invoked"); // TODO: i18n

        Fiber fiber = engine.createFiber();
        // then send it away!
        Tube tube = pool.take();
        try {
            reply = fiber.runSync(tube, packet);
        } finally {
            pool.recycle(tube);
        }

        // not that Packet can still be updated after
        // ResponseContext is created.
        receiver.setResponseContext(new ResponseContext(reply));

        return reply;
    }

    /**
     * Passes a message through a {@link Tube}line for processing. The processing happens
     * asynchronously and when the response is available, Fiber.CompletionCallback is
     * called. The processing could happen on multiple threads.
     *
     * <p>
     * Unlike {@link Tube} instances,
     * this method is thread-safe and can be invoked from
     * multiple threads concurrently.
     *
     * @param request         The message to be sent to the server
     * @param requestContext The {@link RequestContext} when this invocation is originally scheduled.
     *                       This must be the same object as {@link #requestContext} for synchronous
     *                       invocations, but for asynchronous invocations, it needs to be a snapshot
     *                       captured at the point of invocation, to correctly satisfy the spec requirement.
     * @param completionCallback Once the processing is done, the callback is invoked.
     */
    protected final void processAsync(Packet request, RequestContext requestContext, final Fiber.CompletionCallback completionCallback) {
        // fill in Packet
        request.proxy = this;
        request.handlerConfig = binding.getHandlerConfig();
        requestContext.fill(request);
        if (AddressingVersion.isEnabled(binding)) {
            if(endpointReference!=null)
                endpointReference.addReferenceParameters(request.getMessage().getHeaders());
        }

        final Pool<Tube> pool = tubes;
        if (pool == null)
            throw new WebServiceException("close method has already been invoked"); // TODO: i18n

        Fiber fiber = engine.createFiber();
        // then send it away!
        final Tube tube = pool.take();
        fiber.start(tube, request, new Fiber.CompletionCallback() {
            public void onCompletion(@NotNull Packet response) {
                pool.recycle(tube);
                completionCallback.onCompletion(response);
            }
            public void onCompletion(@NotNull Throwable error) {
                // let's not reuse tubes as they might be in a wrong state, so not
                // calling pool.recycle()
                completionCallback.onCompletion(error);
            }
        });
    }

    public void close() {
        if (tubes != null) {
            // multi-thread safety of 'close' needs to be considered more carefully.
            // some calls might be pending while this method is invoked. Should we
            // block until they are complete, or should we abort them (but how?)
            Tube p = tubes.take();
            tubes = null;
            p.preDestroy();
        }
    }

    public final WSBinding getBinding() {
        return binding;
    }

    public final Map<String, Object> getRequestContext() {
        return requestContext.getMapView();
    }

    public final ResponseContext getResponseContext() {
        return responseContext;
    }

    public void setResponseContext(ResponseContext rc) {
        this.responseContext = rc;
    }

    public String toString() {
        return RuntimeVersion.VERSION + ": Stub for " + getRequestContext().get(BindingProvider.ENDPOINT_ADDRESS_PROPERTY);
    }

    public final W3CEndpointReference getEndpointReference() {
        if (binding.getBindingID().equals(HTTPBinding.HTTP_BINDING))
            throw new java.lang.UnsupportedOperationException("BindingProvider.getEndpointReference() not supported with XML/HTTP Binding");
        return getEndpointReference(W3CEndpointReference.class);
    }

    public final <T extends EndpointReference>
    T getEndpointReference(Class<T> clazz) {

        if (binding.getBindingID().equals(HTTPBinding.HTTP_BINDING))
            throw new java.lang.UnsupportedOperationException("BindingProvider.getEndpointReference(Class<T> class) not supported with XML/HTTP Binding");

        // we need to expand WSEndpointAddress class to be able to return EPR with arbitrary address.
        if (endpointReference != null) {
            return endpointReference.toSpec(clazz);
        }
        String eprAddress = requestContext.getEndpointAddress().toString();
        QName portTypeName = null;
        String wsdlAddress = null;
        if(wsdlPort!=null) {
            portTypeName = wsdlPort.getBinding().getPortTypeName();
            wsdlAddress = eprAddress +"?wsdl";
        }
        return new WSEndpointReference(
            AddressingVersion.fromSpecClass(clazz),
            eprAddress, getServiceName(), getPortName(), portTypeName, null, wsdlAddress, null).toSpec(clazz);
    }

//
//
// WSBindingProvider methods
//
//
    public final void setOutboundHeaders(List<Header> headers) {
        if(headers==null) {
            this.userOutboundHeaders = null;
        } else {
            for (Header h : headers) {
                if(h==null)
                    throw new IllegalArgumentException();
            }
            userOutboundHeaders = headers.toArray(new Header[headers.size()]);
        }
    }

    public final void setOutboundHeaders(Header... headers) {
        if(headers==null) {
            this.userOutboundHeaders = null;
        } else {
            for (Header h : headers) {
                if(h==null)
                    throw new IllegalArgumentException();
            }
            Header[] hl = new Header[headers.length];
            System.arraycopy(headers,0,hl,0,headers.length);
            userOutboundHeaders = hl;
        }
    }

    public final List<Header> getInboundHeaders() {
        return Collections.unmodifiableList((HeaderList)
            responseContext.get(JAXWSProperties.INBOUND_HEADER_LIST_PROPERTY));
    }
}
