/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */

package com.sun.xml.ws.transport.http.server;

import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsExchange;
import com.sun.xml.ws.resources.HttpserverMessages;
import com.sun.xml.ws.transport.http.WSHTTPConnection;
import com.sun.xml.ws.transport.http.HttpAdapter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link HttpHandler} implementation that serves the actual request.
 *
 * @author Jitendra Kotamraju
 * @author Kohsuke Kawaguhi
 */
final class WSHttpHandler implements HttpHandler {

    private static final String GET_METHOD = "GET";
    private static final String POST_METHOD = "POST";
    private static final String HEAD_METHOD = "HEAD";
    private static final String PUT_METHOD = "PUT";
    private static final String DELETE_METHOD = "DELETE";

    private static final Logger logger =
        Logger.getLogger(
            com.sun.xml.ws.util.Constants.LoggingDomain + ".server.http");

    private final HttpAdapter adapter;
    private final Executor executor;

    public WSHttpHandler(@NotNull HttpAdapter adapter, @Nullable Executor executor) {
        assert adapter!=null;
        this.adapter = adapter;
        this.executor = executor;
    }
    
    /**
     * Called by HttpServer when there is a matching request for the context
     */
    public void handle(HttpExchange msg) {
        logger.fine("Received HTTP request:"+msg.getRequestURI());
        if (executor != null) {
            // Use application's Executor to handle request. Application may
            // have set an executor using Endpoint.setExecutor().
            executor.execute(new HttpHandlerRunnable(msg));
        } else {
            handleExchange(msg);
        }
    }

    public void handleExchange(HttpExchange msg) {
        WSHTTPConnection con = new ServerConnectionImpl(msg);
        try {
            logger.fine("Received HTTP request:"+msg.getRequestURI());
            String method = msg.getRequestMethod();
            if (method.equals(GET_METHOD)) {
                String queryString = msg.getRequestURI().getQuery();
                logger.fine("Query String for request ="+queryString);
                if (adapter.isMetadataQuery(queryString)) {
                    adapter.publishWSDL(con,getRequestAddress(msg), msg.getRequestURI().getQuery());
                } else {
                    adapter.handle(con);
                }
            } else if (method.equals(POST_METHOD) || method.equals(HEAD_METHOD)
                        || method.equals(PUT_METHOD) || method.equals(DELETE_METHOD)) {
                adapter.handle(con);
            } else {
                logger.warning(HttpserverMessages.UNEXPECTED_HTTP_METHOD(method));
            }
        } catch(IOException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        } finally {
            con.close();
        }
    }

    /**
     * Wrapping the processing of request in a Runnable so that it can be
     * executed in Executor.
     */
    class HttpHandlerRunnable implements Runnable {
        final HttpExchange msg;

        HttpHandlerRunnable(HttpExchange msg) {
            this.msg = msg;
        }

        public void run() {
            handleExchange(msg);
        }
    }


    /**
     * Computes the Endpoint's address from the request. Use "Host" header
     * so that it has correct address(IP address or someother hostname) through
     * which the application reached the endpoint.
     *
     * @return
     *      a string like "http://foo.bar:1234/abc/def"
     */
    private String getRequestAddress(HttpExchange msg) {
        StringBuilder strBuf = new StringBuilder();
        strBuf.append((msg instanceof HttpsExchange) ? "https" : "http");
        strBuf.append("://");

        List<String> hostHeader = msg.getResponseHeaders().get("Host");
        if (hostHeader != null) {
            strBuf.append(hostHeader.get(0));   // Uses Host header
        } else {
            strBuf.append(msg.getLocalAddress().getHostName());
            strBuf.append(":");
            strBuf.append(msg.getLocalAddress().getPort());
        }
        strBuf.append(msg.getRequestURI().getPath());

        return strBuf.toString();
    }
}
