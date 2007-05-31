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
import com.sun.xml.ws.api.model.SEIModel;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.api.pipe.helper.PipeAdapter;
import com.sun.xml.ws.api.server.WSEndpoint;

import java.io.PrintStream;

/**
 * Factory for well-known server {@link Pipe} implementations
 * that the {@link PipelineAssembler} needs to use
 * to satisfy JAX-WS requirements.
 *
 * @author Jitendra Kotamraju
 * @deprecated Use {@link ServerTubeAssemblerContext}.
 */
public final class ServerPipeAssemblerContext extends ServerTubeAssemblerContext {

    public ServerPipeAssemblerContext(@Nullable SEIModel seiModel, @Nullable WSDLPort wsdlModel, @NotNull WSEndpoint endpoint, @NotNull Tube terminal, boolean isSynchronous) {
        super(seiModel, wsdlModel, endpoint, terminal, isSynchronous);
    }

    /**
     * Creates a {@link Pipe} that performs SOAP mustUnderstand processing.
     * This pipe should be before HandlerPipes.
     */
    public @NotNull Pipe createServerMUPipe(@NotNull Pipe next) {
        return PipeAdapter.adapt(super.createServerMUTube(PipeAdapter.adapt(next)));
    }

    /**
     * creates a {@link Pipe} that dumps messages that pass through.
     */
    public Pipe createDumpPipe(String name, PrintStream out, Pipe next) {
        return PipeAdapter.adapt(super.createDumpTube(name, out, PipeAdapter.adapt(next)));
    }

    /**
     * Creates a {@link Pipe} that does the monitoring of the invocation for a
     * container
     */
    public @NotNull Pipe createMonitoringPipe(@NotNull Pipe next) {
        return PipeAdapter.adapt(super.createMonitoringTube(PipeAdapter.adapt(next)));
    }

    /**
     * Creates a {@link Pipe} that adds container specific security
     */
    public @NotNull Pipe createSecurityPipe(@NotNull Pipe next) {
        return PipeAdapter.adapt(super.createSecurityTube(PipeAdapter.adapt(next)));
    }

    /**
     * Creates a {@link Pipe} that invokes protocol and logical handlers.
     */
    public @NotNull Pipe createHandlerPipe(@NotNull Pipe next) {
        return PipeAdapter.adapt(super.createHandlerTube(PipeAdapter.adapt(next)));
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
         return PipeAdapter.adapt(super.getTerminalTube());
    }

     /**
     * Creates WS-Addressing pipe
     */
    public Pipe createWsaPipe(Pipe next) {
        return PipeAdapter.adapt(super.createWsaTube(PipeAdapter.adapt(next)));
    }
}
