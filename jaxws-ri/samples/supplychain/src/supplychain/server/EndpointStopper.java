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

package supplychain.server;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.xml.ws.Endpoint;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EndpointStopper {

    EndpointStopper(final int port, final Endpoint endpoint) throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress(port), 5);
        final ExecutorService threads  = Executors.newFixedThreadPool(2);
        server.setExecutor(threads);
        server.start();

        HttpContext context = server.createContext("/stop");
        context.setHandler(new HttpHandler() {
    		public void handle(HttpExchange msg) throws IOException {
				System.out.println("Shutting down the Endpoint");
				endpoint.stop();
				System.out.println("Endpoint is down");
                msg.sendResponseHeaders(200, 0);
                msg.close();
        		server.stop(1);
        		threads.shutdown();
			}
		});
    }
}
