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

package com.sun.xml.ws.transport.http;

import com.sun.xml.ws.transport.http.DeploymentDescriptorParser.AdapterFactory;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.api.server.PortAddressResolver;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.istack.NotNull;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.AbstractList;

/**
 * List of {@link HttpAdapter}s created together.
 *
 * <p>
 * Some cases WAR file may contain multiple endpoints for ports in a WSDL.
 * If the runtime knows these ports, their port addresses can be patched.
 * This class keeps a list of {@link HttpAdapter}s and use that information to patch
 * multiple port addresses.
 *
 * <p>
 * Concrete implementations of this class need to override {@link #createHttpAdapter}
 * method to create implementations of {@link HttpAdapter}.
 *
 * @author Jitu
 */
public abstract class HttpAdapterList<T extends HttpAdapter> extends AbstractList<T> implements AdapterFactory<T> {
    private final List<T> adapters = new ArrayList<T>();
    private final Map<String, String> addressMap = new HashMap<String, String>();

    // TODO: documented because it's used by AS
    public T createAdapter(String name, String urlPattern, WSEndpoint<?> endpoint) {
        T t = createHttpAdapter(name, urlPattern, endpoint);
        adapters.add(t);
        WSDLPort port = endpoint.getPort();
        if (port != null) {
            addressMap.put(port.getName().getLocalPart(), getValidPath(urlPattern));
        }
        return t;
    }

    /**
     * Implementations need to override this one to create a concrete class
     * of HttpAdapter
     */
    protected abstract T createHttpAdapter(String name, String urlPattern, WSEndpoint<?> endpoint);

    /**
     * @return urlPattern without "/*"
     */
    private String getValidPath(@NotNull String urlPattern) {
        if (urlPattern.endsWith("/*")) {
            return urlPattern.substring(0, urlPattern.length() - 2);
        } else {
            return urlPattern;
        }
    }

    /**
     * Creates a PortAddressResolver that maps portname to its address
     */
    public PortAddressResolver createPortAddressResolver(final String baseAddress) {
        return new PortAddressResolver() {
            public String getAddressFor(@NotNull String portName) {
                String urlPattern = addressMap.get(portName);
                return (urlPattern == null) ? null : baseAddress+urlPattern;
            }
        };
    }


    public T get(int index) {
        return adapters.get(index);
    }

    public int size() {
        return adapters.size();
    }
}
