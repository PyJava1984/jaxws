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
import com.sun.istack.Nullable;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.addressing.AddressingVersion;
import com.sun.xml.ws.api.addressing.WSEndpointReference;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.model.SEIModel;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.api.pipe.Codec;
import com.sun.xml.ws.api.pipe.Engine;
import com.sun.xml.ws.api.pipe.Fiber;
import com.sun.xml.ws.api.pipe.FiberContextSwitchInterceptor;
import com.sun.xml.ws.api.pipe.ServerPipeAssemblerContext;
import com.sun.xml.ws.api.pipe.ServerTubeAssemblerContext;
import com.sun.xml.ws.api.pipe.Tube;
import com.sun.xml.ws.api.pipe.TubeCloner;
import com.sun.xml.ws.api.pipe.TubelineAssembler;
import com.sun.xml.ws.api.pipe.TubelineAssemblerFactory;
import com.sun.xml.ws.api.server.Container;
import com.sun.xml.ws.api.server.TransportBackChannel;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.api.server.WebServiceContextDelegate;
import com.sun.xml.ws.fault.SOAPFaultBuilder;
import com.sun.xml.ws.resources.HandlerMessages;
import com.sun.xml.ws.util.Pool;
import com.sun.xml.ws.util.Pool.TubePool;
import org.w3c.dom.Element;

import javax.annotation.PreDestroy;
import javax.xml.namespace.QName;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.handler.Handler;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link WSEndpoint} implementation.
 *
 * @author Kohsuke Kawaguchi
 * @author Jitendra Kotamraju
 */
public final class WSEndpointImpl<T> extends WSEndpoint<T> {
    private final @NotNull QName serviceName;
    private final @NotNull QName portName;
    private final WSBinding binding;
    private final SEIModel seiModel;
    private final @NotNull Container container;
    private final WSDLPort port;

    private final Tube masterTubeline;
    private final ServiceDefinitionImpl serviceDef;
    private final SOAPVersion soapVersion;
    private final Engine engine;
    private final @NotNull Codec masterCodec;

    private final Pool<Tube> tubePool;

    /**
     * Set to true once we start shutting down this endpoint.
     * Used to avoid running the clean up processing twice.
     *
     * @see #dispose()
     */
    private boolean disposed;

    private final Class<T> implementationClass;

    WSEndpointImpl(@NotNull QName serviceName, @NotNull QName portName, WSBinding binding,
                   Container container, SEIModel seiModel, WSDLPort port,
                   Class<T> implementationClass,
                   @Nullable ServiceDefinitionImpl serviceDef,
                   InvokerTube terminalTube, boolean isSynchronous) {
        this.serviceName = serviceName;
        this.portName = portName;
        this.binding = binding;
        this.soapVersion = binding.getSOAPVersion();
        this.container = container;
        this.port = port;
        this.implementationClass = implementationClass;
        this.serviceDef = serviceDef;
        this.seiModel = seiModel;
        if (serviceDef != null) {
            serviceDef.setOwner(this);
        }

        TubelineAssembler assembler = TubelineAssemblerFactory.create(
                Thread.currentThread().getContextClassLoader(), binding.getBindingId(), container);
        assert assembler!=null;

        ServerTubeAssemblerContext context = new ServerPipeAssemblerContext(seiModel, port, this, terminalTube, isSynchronous);
        this.masterTubeline = assembler.createServer(context);
        this.masterCodec = context.getCodec();
        tubePool = new TubePool(masterTubeline);
        terminalTube.setEndpoint(this);
        engine = new Engine(toString());
    }

    public @NotNull Class<T> getImplementationClass() {
        return implementationClass;
    }

    public @NotNull WSBinding getBinding() {
        return binding;
    }

    public @NotNull Container getContainer() {
        return container;
    }

    public WSDLPort getPort() {
        return port;
    }

    /**
     * Gets the {@link SEIModel} that represents the relationship
     * between WSDL and Java SEI.
     *
     * <p>
     * This method returns a non-null value if and only if this
     * endpoint is ultimately serving an application through an SEI.
     *
     * @return
     *      maybe null. See above for more discussion.
     *      Always the same value.
     */
    public @Nullable SEIModel getSEIModel() {
        return seiModel;
    }

    public void setExecutor(Executor exec) {
        engine.setExecutor(exec);
    }

    public void schedule(final Packet request, final CompletionCallback callback, FiberContextSwitchInterceptor interceptor) {
        request.endpoint = WSEndpointImpl.this;
        Fiber fiber = engine.createFiber();
        if (interceptor != null) {
            fiber.addInterceptor(interceptor);
        }
        final Tube tube = tubePool.take();
        fiber.start(tube, request, new Fiber.CompletionCallback() {
            public void onCompletion(@NotNull Packet response) {
                tubePool.recycle(tube);
                if (callback!=null) {
                    callback.onCompletion(response);
                }
            }

            public void onCompletion(@NotNull Throwable error) {
                // let's not reuse tubes as they might be in a wrong state, so not
                // calling tubePool.recycle()
                error.printStackTrace();
                // Convert all runtime exceptions to Packet so that transport doesn't
                // have to worry about converting to wire message
                // TODO XML/HTTP binding
                Message faultMsg = SOAPFaultBuilder.createSOAPFaultMessage(
                        soapVersion, null, error);
                Packet response = request.createServerResponse(faultMsg, request.endpoint.getPort(),
                        request.endpoint.getBinding());
                if (callback!=null) {
                    callback.onCompletion(response);
                }
            }
        });
    }

    public @NotNull PipeHead createPipeHead() {
        return new PipeHead() {
            private final Tube tube = TubeCloner.clone(masterTubeline);

            public @NotNull Packet process(Packet request, WebServiceContextDelegate wscd, TransportBackChannel tbc) {
                request.webServiceContextDelegate = wscd;
                request.transportBackChannel = tbc;
                request.endpoint = WSEndpointImpl.this;
                Fiber fiber = engine.createFiber();
                Packet response;
                try {
                    response = fiber.runSync(tube,request);
                } catch (RuntimeException re) {
                    // Catch all runtime exceptions so that transport doesn't
                    // have to worry about converting to wire message
                    // TODO XML/HTTP binding
                    re.printStackTrace();
                    Message faultMsg = SOAPFaultBuilder.createSOAPFaultMessage(
                            soapVersion, null, re);
                    response = request.createServerResponse(faultMsg, request.endpoint.getPort(), request.endpoint.getBinding());
                }
                return response;
            }
        };
    }

    public synchronized void dispose() {
        if(disposed)
            return;
        disposed = true;

        masterTubeline.preDestroy();

        for (Handler handler : binding.getHandlerChain()) {
            for (Method method : handler.getClass().getMethods()) {
                if (method.getAnnotation(PreDestroy.class) == null) {
                    continue;
                }
                try {
                    method.invoke(handler);
                } catch (Exception e) {
                    logger.log(Level.WARNING, HandlerMessages.HANDLER_PREDESTROY_IGNORE(e.getMessage()), e);
                }
                break;
            }
        }
    }

    public ServiceDefinitionImpl getServiceDefinition() {
        return serviceDef;
    }

    private static final Logger logger = Logger.getLogger(
        com.sun.xml.ws.util.Constants.LoggingDomain + ".server.endpoint");

    public <T extends EndpointReference> T getEndpointReference(Class<T> clazz, String address, String wsdlAddress, Element...referenceParameters) {
        QName portType = null;
        if(port != null) {
            portType = port.getBinding().getPortTypeName();            
        }
        List<Element> refParams = null;
        if(referenceParameters != null) {
            refParams = Arrays.asList(referenceParameters);
        }
        return new WSEndpointReference(
            AddressingVersion.fromSpecClass(clazz),
            address, serviceName, portName, portType, null, wsdlAddress, refParams).toSpec(clazz);
    }

    public @NotNull QName getPortName() {
        return portName;
    }


    public @NotNull Codec createCodec() {
        return masterCodec.copy();
    }

    public @NotNull QName getServiceName() {
        return serviceName;
    }
}
