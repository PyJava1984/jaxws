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
 * Copyright 2007 Sun Microsystems Inc. All Rights Reserved
 */

package com.sun.xml.ws.api.pipe;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.addressing.WsaServerTube;
import com.sun.xml.ws.api.addressing.AddressingVersion;
import com.sun.xml.ws.api.model.SEIModel;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.api.pipe.helper.PipeAdapter;
import com.sun.xml.ws.api.server.ServerPipelineHook;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.binding.BindingImpl;
import com.sun.xml.ws.handler.HandlerTube;
import com.sun.xml.ws.handler.ServerLogicalHandlerTube;
import com.sun.xml.ws.handler.ServerSOAPHandlerTube;
import com.sun.xml.ws.protocol.soap.ServerMUTube;
import com.sun.xml.ws.util.pipe.DumpTube;

import javax.xml.ws.soap.SOAPBinding;
import java.io.PrintStream;

/**
 * Factory for well-known server {@link Tube} implementations
 * that the {@link TubelineAssembler} needs to use
 * to satisfy JAX-WS requirements.
 *
 * @author Jitendra Kotamraju
 */
public class ServerTubeAssemblerContext {

    private final SEIModel seiModel;
    private final WSDLPort wsdlModel;
    private final WSEndpoint endpoint;
    private final BindingImpl binding;
    private final Tube terminal;
    private final boolean isSynchronous;
    private @NotNull Codec codec;

    public ServerTubeAssemblerContext(@Nullable SEIModel seiModel,
                                      @Nullable WSDLPort wsdlModel, @NotNull WSEndpoint endpoint,
                                      @NotNull Tube terminal, boolean isSynchronous) {
        this.seiModel = seiModel;
        this.wsdlModel = wsdlModel;
        this.endpoint = endpoint;
        this.terminal = terminal;
        // WSBinding is actually BindingImpl
        this.binding = (BindingImpl)endpoint.getBinding();
        this.isSynchronous = isSynchronous;
        this.codec = this.binding.createCodec();
    }

    /**
     * The created pipeline will use seiModel to get java concepts for the endpoint
     *
     * @return Null if the service doesn't have SEI model e.g. Provider endpoints,
     *         and otherwise non-null.
     */
    public @Nullable SEIModel getSEIModel() {
        return seiModel;
    }

    /**
     * The created pipeline will be used to serve this port.
     *
     * @return Null if the service isn't associated with any port definition in WSDL,
     *         and otherwise non-null.
     */
    public @Nullable WSDLPort getWsdlModel() {
        return wsdlModel;
    }

    /**
     *
     * The created pipeline is used to serve this {@link com.sun.xml.ws.api.server.WSEndpoint}.
     * Specifically, its {@link com.sun.xml.ws.api.WSBinding} should be of interest to  many
     * {@link com.sun.xml.ws.api.pipe.Pipe}s.
     *  @return Always non-null.
     */
    public @NotNull WSEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * The last {@link com.sun.xml.ws.api.pipe.Pipe} in the pipeline. The assembler is expected to put
     * additional {@link com.sun.xml.ws.api.pipe.Pipe}s in front of it.
     *
     * <p>
     * (Just to give you the idea how this is used, normally the terminal pipe
     * is the one that invokes the user application or {@link javax.xml.ws.Provider}.)
     *
     * @return always non-null terminal pipe
     */
     public @NotNull Tube getTerminalTube() {
         return terminal;
    }

    /**
     * If this server pipeline is known to be used for serving synchronous transport,
     * then this method returns true. This can be potentially use as an optimization
     * hint, since often synchronous versions are cheaper to execute than asycnhronous
     * versions.
     */
    public boolean isSynchronous() {
        return isSynchronous;
    }

    /**
     * Creates a {@link Tube} that performs SOAP mustUnderstand processing.
     * This pipe should be before HandlerPipes.
     */
    public @NotNull Tube createServerMUTube(@NotNull Tube next) {
        if (binding instanceof SOAPBinding)
            return new ServerMUTube(binding,next);
        else
            return next;
    }

    /**
     * Creates a {@link Tube} that invokes protocol and logical handlers.
     */
    public @NotNull Tube createHandlerTube(@NotNull Tube next) {
        if (!binding.getHandlerChain().isEmpty()) {
            HandlerTube cousin = new ServerLogicalHandlerTube(binding, wsdlModel, next);
            next = cousin;
            if (binding instanceof SOAPBinding) {
                return new ServerSOAPHandlerTube(binding, next, cousin);
            }
        }
        return next;
    }

    /**
     * Creates a {@link Tube} that does the monitoring of the invocation for a
     * container
     */
    public @NotNull Tube createMonitoringTube(@NotNull Tube next) {
        ServerPipelineHook hook = endpoint.getContainer().getSPI(ServerPipelineHook.class);
        if (hook != null) {
            ServerPipeAssemblerContext ctxt = new ServerPipeAssemblerContext(seiModel, wsdlModel, endpoint, terminal, isSynchronous);
            return PipeAdapter.adapt(hook.createMonitoringPipe(ctxt, PipeAdapter.adapt(next)));
        }
        return next;
    }

    /**
     * Creates a {@link Tube} that adds container specific security
     */
    public @NotNull Tube createSecurityTube(@NotNull Tube next) {
        ServerPipelineHook hook = endpoint.getContainer().getSPI(ServerPipelineHook.class);
        if (hook != null) {
            ServerPipeAssemblerContext ctxt = new ServerPipeAssemblerContext(seiModel, wsdlModel, endpoint, terminal, isSynchronous);
            return PipeAdapter.adapt(hook.createSecurityPipe(ctxt, PipeAdapter.adapt(next)));
        }
        return next;
    }

    /**
     * creates a {@link Tube} that dumps messages that pass through.
     */
    public Tube createDumpTube(String name, PrintStream out, Tube next) {
        return new DumpTube(name, out, next);
    }

    /**
     * Creates WS-Addressing pipe
     */
    public Tube createWsaTube(Tube next) {
        if (binding instanceof SOAPBinding && AddressingVersion.isEnabled(binding) && wsdlModel!=null)
            return new WsaServerTube(wsdlModel, binding, next);
        else
            return next;
    }

    /**
     * Gets the {@link Codec} that is set by {@link #setCodec} or the default codec
     * based on the binding. The codec is a full codec that is responsible for
     * encoding/decoding entire protocol message(for e.g: it is responsible to
     * encode/decode entire MIME messages in SOAP binding)
     *
     * @return codec to be used for web service requests
     * @see {@link Codecs}
     */
    public @NotNull Codec getCodec() {
        return codec;
    }

    /**
     * Interception point to change {@link Codec} during {@link Tube}line assembly. The
     * new codec will be used by jax-ws server runtime for encoding/decoding web service
     * request/response messages. {@link WSEndpoint#createCodec()} will return a copy
     * of this new codec and will be used in the server runtime.
     *
     * <p>
     * The codec is a full codec that is responsible for
     * encoding/decoding entire protocol message(for e.g: it is responsible to
     * encode/decode entire MIME messages in SOAP binding)
     *
     * <p>
     * the codec should correctly implement {@link Codec#copy} since it is used while
     * serving requests concurrently.
     *
     * @param codec codec to be used for web service requests
     * @see {@link Codecs}
     */
    public void setCodec(@NotNull Codec codec) {
        this.codec = codec;
    }

}
