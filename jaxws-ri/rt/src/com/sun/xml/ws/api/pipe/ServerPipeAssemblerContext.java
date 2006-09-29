package com.sun.xml.ws.api.pipe;

import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.addressing.MemberSubmissionAddressingFeature;
import com.sun.xml.ws.api.model.SEIModel;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.api.server.ServerPipelineHook;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.protocol.soap.ServerMUPipe;
import com.sun.xml.ws.addressing.WsaServerPipe;
import com.sun.xml.ws.handler.ServerSOAPHandlerPipe;
import com.sun.xml.ws.handler.ServerLogicalHandlerPipe;
import com.sun.xml.ws.handler.HandlerPipe;
import com.sun.xml.ws.model.wsdl.WSDLPortImpl;
import com.sun.xml.ws.binding.SOAPBindingImpl;

import javax.xml.ws.soap.SOAPBinding;
import javax.xml.ws.soap.AddressingFeature;

/**
 * Factory for well-known server {@link Pipe} implementations
 * that the {@link PipelineAssembler} needs to use
 * to satisfy JAX-WS requirements.
 *
 * @author Jitendra Kotamraju
 */
public final class ServerPipeAssemblerContext {

    private final SEIModel seiModel;
    private final WSDLPort wsdlModel;
    private final WSEndpoint endpoint;
    private final WSBinding binding;
    private final Pipe terminal;
    private final boolean isSynchronous;

    public ServerPipeAssemblerContext(@Nullable SEIModel seiModel,
                                      @Nullable WSDLPort wsdlModel, @NotNull WSEndpoint endpoint,
                                      @NotNull Pipe terminal, boolean isSynchronous) {

        this.seiModel = seiModel;
        this.wsdlModel = wsdlModel;
        this.endpoint = endpoint;
        this.terminal = terminal;
        this.binding = endpoint.getBinding();
        this.isSynchronous = isSynchronous;
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
     * The created pipeline is used to serve this {@link WSEndpoint}.
     * Specifically, its {@link WSBinding} should be of interest to  many
     * {@link Pipe}s.
     *  @return Always non-null.
     */
    public @NotNull WSEndpoint getEndpoint() {
        return endpoint;
    }

    /**
     * Creates a {@link Pipe} that performs SOAP mustUnderstand processing.
     * This pipe should be before HandlerPipes.
     */
    public @NotNull Pipe createServerMUPipe(@NotNull Pipe next) {
        if (binding instanceof SOAPBinding)
            return new ServerMUPipe(binding,next);
        else
            return next;
    }

    /**
     * Creates a {@link Pipe} that does the monitoring of the invocation for a
     * container
     */
    public @NotNull Pipe createMonitoringPipe(@NotNull Pipe next) {
        ServerPipelineHook hook = endpoint.getContainer().getSPI(ServerPipelineHook.class);
        if (hook != null)
            return hook.createMonitoringPipe(this, next);
        return next;
    }

    /**
     * Creates a {@link Pipe} that adds container specific security
     */
    public @NotNull Pipe createSecurityPipe(@NotNull Pipe next) {
        ServerPipelineHook hook = endpoint.getContainer().getSPI(ServerPipelineHook.class);
        if (hook != null)
            return hook.createSecurityPipe(this, next);
        return next;
    }

    /**
     * Creates a {@link Pipe} that invokes protocol and logical handlers.
     */
    public @NotNull Pipe createHandlerPipe(@NotNull Pipe next) {
        if (!binding.getHandlerChain().isEmpty()) {
            HandlerPipe cousin = new ServerLogicalHandlerPipe(binding, wsdlModel, next);
            next = cousin;
            if (binding instanceof SOAPBinding) {
                return new ServerSOAPHandlerPipe(binding, next, cousin);
            }
        }
        return next;
    }

    /**
     * Creates WS-Addressing pipe
     */
    public Pipe createWsaPipe(Pipe next) {
        if (binding.hasFeature(MemberSubmissionAddressingFeature.ID) ||
                binding.hasFeature(AddressingFeature.ID)) {
                //kw todo: AddressingFeature
             //  binding.hasFeature())
            if (binding.isFeatureEnabled(MemberSubmissionAddressingFeature.ID) ||
                    binding.isFeatureEnabled(AddressingFeature.ID))
                return new WsaServerPipe(wsdlModel, binding, next);
            else
                return next;
        }

        if (wsdlModel != null) {
            if (((WSDLPortImpl)wsdlModel).isAddressingEnabled())
                return new WsaServerPipe(wsdlModel, binding, next);
        }
        return next;
    }

    /**
     * The last {@link Pipe} in the pipeline. The assembler is expected to put
     * additional {@link Pipe}s in front of it.
     *
     * <p>
     * (Just to give you the idea how this is used, normally the terminal pipe
     * is the one that invokes the user application or {@link javax.xml.ws.Provider}.)
     *
     * @return always non-null terminal pipe
     */
     public @NotNull Pipe getTerminalPipe() {
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
}
