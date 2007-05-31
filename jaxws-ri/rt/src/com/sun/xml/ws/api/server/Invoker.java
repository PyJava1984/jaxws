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

package com.sun.xml.ws.api.server;

import com.sun.istack.NotNull;
import com.sun.xml.ws.api.message.Packet;

import javax.xml.ws.Provider;
import javax.xml.ws.WebServiceContext;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Hides the detail of calling into application endpoint implementation.
 *
 * <p>
 * Typical host of the JAX-WS RI would want to use
 * {@link InstanceResolver#createDefault(Class)} and then
 * use <tt>{@link InstanceResolver#createInvoker()} to obtain
 * the default invoker implementation.
 *
 *
 * @author Jitendra Kotamraju
 * @author Kohsuke Kawaguchi
 */
public abstract class Invoker {
    /**
     * Called by {@link WSEndpoint} when it's set up.
     *
     * <p>
     * This is an opportunity for {@link Invoker}
     * to do a endpoint-specific initialization process.
     *
     * @param wsc
     *      The {@link WebServiceContext} instance that can be injected
     *      to the user instances.
     * @param endpoint
     */
    public void start(@NotNull WSWebServiceContext wsc, @NotNull WSEndpoint endpoint) {
        // backward compatibility
        start(wsc);
    }

    /**
     * @deprecated
     *      Use {@link #start(WSWebServiceContext,WSEndpoint)}
     */
    public void start(@NotNull WebServiceContext wsc) {
        throw new IllegalStateException("deprecated version called");
    }

    /**
     * Called by {@link WSEndpoint}
     * when {@link WSEndpoint#dispose()} is called.
     *
     * This allows {@link InstanceResolver} to do final clean up.
     *
     * <p>
     * This method is guaranteed to be only called once by {@link WSEndpoint}.
     */
    public void dispose() {}

    /**
     *
     */
    public abstract Object invoke( @NotNull Packet p, @NotNull Method m, @NotNull Object... args ) throws InvocationTargetException, IllegalAccessException;

    /**
     * Invokes {@link Provider#invoke(Object)}
     */
    public <T> T invokeProvider( @NotNull Packet p, T arg ) throws IllegalAccessException, InvocationTargetException {
        // default slow implementation that delegates to the other invoke method.
        return (T)invoke(p,invokeMethod,arg);
    }

    /**
     * Invokes {@link AsyncProvider#invoke(Object, AsyncProviderCallback, WebServiceContext)}
     */
    public <T> void invokeAsyncProvider( @NotNull Packet p, T arg, AsyncProviderCallback cbak, WebServiceContext ctxt ) throws IllegalAccessException, InvocationTargetException {
        // default slow implementation that delegates to the other invoke method.
        invoke(p, asyncInvokeMethod, arg, cbak, ctxt);
    }

    private static final Method invokeMethod;

    static {
        try {
            invokeMethod = Provider.class.getMethod("invoke",Object.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static final Method asyncInvokeMethod;

    static {
        try {
            asyncInvokeMethod = AsyncProvider.class.getMethod("invoke",Object.class, AsyncProviderCallback.class, WebServiceContext.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }
}
