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

package com.sun.xml.ws.api.server;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.api.pipe.Fiber.CompletionCallback;
import com.sun.xml.ws.api.pipe.FiberContextSwitchInterceptor;
import com.sun.xml.ws.api.pipe.Pipe;
import com.sun.xml.ws.api.pipe.ServerPipeAssemblerContext;
import com.sun.xml.ws.server.EndpointFactory;
import com.sun.xml.ws.util.xml.XmlUtil;
import org.xml.sax.EntityResolver;

import javax.xml.namespace.QName;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.WebServiceException;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.Executor;

/**
 * Root object that hosts the {@link Packet} processing code
 * at the server.
 *
 * <p>
 * One instance of {@link WSEndpoint} is created for each deployed service
 * endpoint. A hosted service usually handles multiple concurrent
 * requests. To do this efficiently, an endpoint handles incoming
 * {@link Packet} through {@link PipeHead}s, where many copies can be created
 * for each endpoint.
 *
 * <p>
 * Each {@link PipeHead} is thread-unsafe, and request needs to be
 * serialized. A {@link PipeHead} represents a sizable resource
 * (in particular a whole pipeline), so the caller is expected to
 * reuse them and avoid excessive allocations as much as possible.
 * Making {@link PipeHead}s thread-unsafe allow the JAX-WS RI internal to
 * tie thread-local resources to {@link PipeHead}, and reduce the total
 * resource management overhead.
 *
 * <p>
 * To abbreviate this resource management (and for a few other reasons),
 * JAX-WS RI provides {@link Adapter} class. If you are hosting a JAX-WS
 * service, you'll most likely want to send requests to {@link WSEndpoint}
 * through {@link Adapter}.
 *
 * <p>
 * {@link WSEndpoint} is ready to handle {@link Packet}s as soon as
 * it's created. No separate post-initialization step is necessary.
 * However, to comply with the JAX-WS spec requirement, the caller
 * is expected to call the {@link #dispose()} method to allow an
 * orderly shut-down of a hosted service.
 *
 *
 *
 * <h3>Objects Exposed From Endpoint</h3>
 * <p>
 * {@link WSEndpoint} exposes a series of information that represents
 * how an endpoint is configured to host a service. See the getXXX methods
 * for more details.
 *
 *
 *
 * <h3>Implementation Notes</h3>
 * <p>
 * {@link WSEndpoint} owns a {@link WebServiceContext} implementation.
 * But a bulk of the work is delegated to {@link WebServiceContextDelegate},
 * which is passed in as a parameter to {@link PipeHead#process(Packet, WebServiceContextDelegate, TransportBackChannel)}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class WSEndpoint<T> {

    /**
     * Gets the application endpoint {@link Class} that eventually serves the request.
     *
     * <p>
     * This is the same value given to the {@link #create} method.
     */
    public abstract @NotNull Class<T> getImplementationClass();

    /**
     * Represents the binding for which this {@link WSEndpoint}
     * is created for.
     *
     * @return
     *      always same object.
     */
    public abstract @NotNull WSBinding getBinding();

    /**
     * Gets the {@link Container} object.
     *
     * <p>
     * The components inside {@link WSEndpoint} uses this reference
     * to communicate with the hosting environment.
     *
     * @return
     *      always same object. If no "real" {@link Container} instance
     *      is given, {@link Container#NONE} will be returned.
     */
    public abstract @NotNull Container getContainer();

    /**
     * Gets the port that this endpoint is serving.
     *
     * <p>
     * A service is not required to have a WSDL, and when it doesn't,
     * this method returns null. Otherwise it returns an object that
     * describes the port that this {@link WSEndpoint} is serving.
     *
     * @return
     *      Possibly null, but always the same value.
     */
    public abstract @Nullable WSDLPort getPort();

    /**
     * TODO: javadoc
     */
    public abstract void setExecutor(Executor exec);

    /**
     * TODO: javadoc
     */
    public final void schedule( Packet request, CompletionCallback callback ) {
        schedule(request,callback,null);
    }

    /**
     * TODO: javadoc
     */
    public abstract void schedule( Packet request, CompletionCallback callback, FiberContextSwitchInterceptor interceptor );

    /**
     * Creates a new {@link PipeHead} to process
     * incoming requests.
     *
     * <p>
     * This is not a cheap operation. The caller is expected
     * to reuse the returned {@link PipeHead}. See
     * {@link WSEndpoint class javadoc} for details.
     *
     * @return
     *      A newly created {@link PipeHead} that's ready to serve.
     */
    public abstract @NotNull PipeHead createPipeHead();

    /**
     * Represents a resource local to a thread.
     *
     * See {@link WSEndpoint} class javadoc for more discussion about
     * this.
     */
    public interface PipeHead {
        /**
         * Processes a request and produces a reply.
         *
         * <p>
         * This method takes a {@link Packet} that represents
         * a request, run it through a {@link Pipe}line, eventually
         * pass it to the user implementation code, which produces
         * a reply, then run that through the pipeline again,
         * and eventually return it as a return value.
         *
         * @param request
         *      Unconsumed {@link Packet} that represents
         *      a request.
         * @param wscd
         *      {@link WebServiceContextDelegate} to be set to {@link Packet}.
         *      (we didn't have to take this and instead just ask the caller to
         *      set to {@link Packet#webServiceContextDelegate}, but that felt
         *      too error prone.)
         * @param tbc
         *      {@link TransportBackChannel} to be set to {@link Packet}.
         *      See the {@code wscd} parameter javadoc for why this is a parameter.
         *      Can be null.
         * @return
         *      Unconsumed {@link Packet} that represents
         *      a reply to the request.
         *
         * @throws WebServiceException
         *      This method <b>does not</b> throw a {@link WebServiceException}.
         *      The {@link WSEndpoint} must always produce a fault {@link Message}
         *      for it.
         *
         * @throws RuntimeException
         *      A {@link RuntimeException} thrown from this method, including
         *      {@link WebServiceException}, must be treated as a bug in the
         *      code (including JAX-WS and all the pipe implementations), not
         *      an operator error by the user.
         *
         *      <p>
         *      Therefore, it should be recorded by the caller in a way that
         *      allows developers to fix a bug.
         */
        @NotNull Packet process(
            @NotNull Packet request, @Nullable WebServiceContextDelegate wscd, @Nullable TransportBackChannel tbc);
    }

    /**
     * Indicates that the {@link WSEndpoint} is about to be turned off,
     * and will no longer serve any packet anymore.
     *
     * <p>
     * This method needs to be invoked for the JAX-WS RI to correctly
     * implement some of the spec semantics (TODO: pointer.)
     * It's the responsibility of the code that hosts a {@link WSEndpoint}
     * to invoke this method.
     *
     * <p>
     * Once this method is called, the behavior is undefed for
     * all in-progress {@link PipeHead#process} methods (by other threads)
     * and future {@link PipeHead#process} method invocations.
     */
    public abstract void dispose();

    /**
     * Gets the description of the service.
     *
     * <p>
     * A description is a set of WSDL/schema and other documents that together
     * describes a service.
     * A service is not required to have a description, and when it doesn't,
     * this method returns null.
     *
     * @return
     *      Possibly null, but always the same value.
     */
    public abstract @Nullable ServiceDefinition getServiceDefinition();



    /**
     * Used for "from-Java" deployment.
     *
     * <p>
     * This method works like the following:
     * <ol>
     * <li>{@link ServiceDefinition} is modeleed from the given SEI type.
     * <li>{@link InstanceResolver} that always serves <tt>implementationObject</tt> will be used.
     * <li>TODO: where does the binding come from?
     * </ol>
     * @param processHandlerAnnotation
     *          Flag to control processing of @HandlerChain on Impl class
     *          if true, processes @HandlerChain on Impl
     *          if false, DD might have set HandlerChain no need to parse.
     * @param resolver
     *      Optional resolver used to de-reference resources referenced from
     *      WSDL. Must be null if the {@code url} is null.
     * @param serviceName
     *      Optional service name to override the one given by the implementation class.
     * @param portName
     *      Optional port name to override the one given by the implementation class.
     *
     * TODO: DD has a configuration for MTOM threshold.
     * Maybe we need something more generic so that other technologies
     * like Tango can get information from DD.
     *
     * TODO: does it really make sense for this to take EntityResolver?
     * Given that all metadata has to be given as a list anyway.
     *
     * @param primaryWsdl
     *      The {@link ServiceDefinition#getPrimary() primary} WSDL.
     *      If null, it'll be generated based on the SEI (if this is an SEI)
     *      or no WSDL is associated (if it's a provider.)
     *      TODO: shouldn't the implementation find this from the metadata list?
     * @param metadata
     *      Other documents that become {@link SDDocument}s. Can be null.
     * @param isTransportSynchronous
     *      If the caller knows that the returned {@link WSEndpoint} is going to be
     *      used by a synchronous-only transport, then it may pass in <tt>true</tt>
     *      to allow the callee to perform an optimization based on that knowledge
     *      (since often synchronous version is cheaper than an asynchronous version.)
     *      This value is visible from {@link ServerPipeAssemblerContext#isSynchronous()}.
     *
     * @return newly constructed {@link WSEndpoint}.
     * @throws WebServiceException
     *      if the endpoint set up fails.
     */
    public static <T> WSEndpoint<T> create(
        @NotNull Class<T> implType,
        boolean processHandlerAnnotation,
        @NotNull Invoker invoker,
        @NotNull QName serviceName,
        @NotNull QName portName,
        @Nullable Container container,
        @NotNull WSBinding binding,
        @Nullable SDDocumentSource primaryWsdl,
        @Nullable Collection<? extends SDDocumentSource> metadata,
        @Nullable EntityResolver resolver,
        boolean isTransportSynchronous) {
        return EndpointFactory.createEndpoint(
            implType,processHandlerAnnotation, invoker,serviceName,portName,container,binding,primaryWsdl,metadata,resolver,isTransportSynchronous);
    }

    /**
     * Deprecated version that assumes <tt>isTransportSynchronous==false</tt>
     */
    @Deprecated
    public static <T> WSEndpoint<T> create(
        @NotNull Class<T> implType,
        boolean processHandlerAnnotation,
        @NotNull Invoker invoker,
        @NotNull QName serviceName,
        @NotNull QName portName,
        @Nullable Container container,
        @NotNull WSBinding binding,
        @Nullable SDDocumentSource primaryWsdl,
        @Nullable Collection<? extends SDDocumentSource> metadata,
        @Nullable EntityResolver resolver) {
        return create(implType,processHandlerAnnotation,invoker,serviceName,portName,container,binding,primaryWsdl,metadata,resolver,false);
    }


    /**
     * The same as
     * {@link #create(Class, boolean, Invoker, QName, QName, Container, WSBinding, SDDocumentSource, Collection, EntityResolver)}
     * except that this version takes an url of the <tt>jax-ws-catalog.xml</tt>.
     *
     * @param catalogUrl
     *      if not null, an {@link EntityResolver} is created from it and used.
     *      otherwise no resolution will be performed.
     */
    public static <T> WSEndpoint<T> create(
        Class<T> implType,
        boolean processHandlerAnnotation,
        Invoker invoker,
        QName serviceName,
        QName portName,
        Container container,
        WSBinding binding,
        SDDocumentSource primaryWsdl,
        Collection<? extends SDDocumentSource> metadata,
        @Nullable URL catalogUrl) {
        return create(
            implType,processHandlerAnnotation,invoker,serviceName,portName,container,binding,primaryWsdl,metadata,
            XmlUtil.createEntityResolver(catalogUrl));
    }

    public abstract <T extends EndpointReference> T getEndpointReference(
            Class<T> clazz,
            String address);
}
