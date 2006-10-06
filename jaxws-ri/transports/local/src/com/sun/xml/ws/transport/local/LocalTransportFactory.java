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

package com.sun.xml.ws.transport.local;

import com.sun.istack.NotNull;
import com.sun.xml.ws.api.pipe.ClientTubeAssemblerContext;
import com.sun.xml.ws.api.pipe.TransportPipeFactory;
import com.sun.xml.ws.api.pipe.TransportTubeFactory;
import com.sun.xml.ws.api.pipe.Tube;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.transport.http.DeploymentDescriptorParser;
import com.sun.xml.ws.transport.http.DeploymentDescriptorParser.AdapterFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

/**
 * {@link TransportPipeFactory} for the local transport.
 *
 * <p>
 * The syntax of the endpoint address is:
 * <pre><xmp>
 * local:///path/to/exploded/war/image?portLocalName
 * </xmp></pre>
 *
 * <p>
 * If the service only contains one port, the <tt>?portLocalName</tt> portion
 * can be omitted.
 *
 * @author Kohsuke Kawaguchi
 */
public final class LocalTransportFactory extends TransportTubeFactory {
    public Tube doCreate(@NotNull ClientTubeAssemblerContext context) {
        URI adrs = context.getAddress().getURI();
        if(!adrs.getScheme().equals("local"))
            return null;

        return new LocalTransportTube(adrs,createServerService(adrs),context.getBinding());
    }

    /**
     * The local transport works by looking at the exploded war file image on
     * a file system.
     * TODO: Currently it expects the PortName to be appended to the endpoint address
     *       This needs to be expanded to take Service and Port QName as well.
     */
    protected static WSEndpoint createServerService(URI adrs) {
        try {
            String outputDir = adrs.getPath();
            List<WSEndpoint> endpoints = parseEndpoints(outputDir);

            WSEndpoint endpoint = endpoints.get(0);
            if (endpoints.size() > 1) {
                for (WSEndpoint rei : endpoints) {
                    //TODO: for now just compare local part
                    if(rei.getPort().getName().getLocalPart().equals(adrs.getQuery())) {
                        endpoint = rei;
                        break;
                    }
                }
            }

            return endpoint;
        } catch (IOException e) {
            throw new Error(e);
        }
    }

    protected static List<WSEndpoint> parseEndpoints(String outputDir) throws IOException {
        String riFile = outputDir+"/WEB-INF/sun-jaxws.xml";
        DeploymentDescriptorParser<WSEndpoint> parser = new DeploymentDescriptorParser<WSEndpoint>(
            Thread.currentThread().getContextClassLoader(),
            new FileSystemResourceLoader(new File(outputDir)), null,
            new AdapterFactory<WSEndpoint>() {
                public WSEndpoint createAdapter(String name, String urlPattern, WSEndpoint<?> endpoint) {
                    return endpoint;
                }
            });
        InputStream in = new FileInputStream(riFile);
        List<WSEndpoint> endpoints = parser.parse(in);
        in.close();

        return endpoints;
    }
}
