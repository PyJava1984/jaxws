/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.xml.ws.transport.http.client;

import com.sun.xml.ws.api.EndpointAddress;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.client.BindingProviderProperties;
import static com.sun.xml.ws.client.BindingProviderProperties.*;
import com.sun.xml.ws.client.ClientTransportException;
import com.sun.xml.ws.resources.ClientMessages;
import com.sun.xml.ws.transport.Headers;
import com.sun.xml.ws.developer.JAXWSProperties;
import com.sun.xml.ws.util.RuntimeVersion;
import com.sun.istack.Nullable;
import com.sun.istack.NotNull;

import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import static javax.xml.bind.DatatypeConverter.printBase64Binary;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.ws.BindingProvider;
import static javax.xml.ws.BindingProvider.SESSION_MAINTAIN_PROPERTY;
import javax.xml.ws.WebServiceException;
import javax.xml.ws.handler.MessageContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;

/**
 * TODO: this class seems to be pointless. Just merge it with {@link HttpTransportPipe}.
 *
 * @author WS Development Team
 */
final class HttpClientTransport {
    
    // Need to use JAXB first to register DatatypeConverter
    static {
        try {
            JAXBContext.newInstance().createUnmarshaller();
        } catch(JAXBException je) {
            // Nothing much can be done. Intentionally left empty
        }
    }
    private static String LAST_ENDPOINT = "";
    private static boolean redirect = true;
    private static final int START_REDIRECT_COUNT = 3;
    private static int redirectCount = START_REDIRECT_COUNT;

    /*package*/ int statusCode;
    private final Map<String, List<String>> reqHeaders;
    private Map<String, List<String>> respHeaders = null;

    private OutputStream outputStream;
    private boolean https;
    private HttpURLConnection httpConnection = null;
    private EndpointAddress endpoint = null;
    private Packet context = null;
    private CookieJar cookieJar = null;
    private boolean isFailure = false;
    private final Integer chunkSize;


    public HttpClientTransport(@NotNull Packet packet, @NotNull Map<String,List<String>> reqHeaders) {
        endpoint = packet.endpointAddress;
        context = packet;
        this.reqHeaders = reqHeaders;
        chunkSize = (Integer)context.invocationProperties.get(JAXWSProperties.HTTP_CLIENT_STREAMING_CHUNK_SIZE);
    }

    /**
     * Prepare the stream for HTTP request
     */
    public OutputStream getOutput() {
        try {
            createHttpConnection();
            sendCookieAsNeeded();
            // for "GET" request no need to get outputStream
            if (requiresOutputStream()) {
                outputStream = httpConnection.getOutputStream();
                if (chunkSize != null) {
                    outputStream = new WSChunkedOuputStream(outputStream, chunkSize);
                }
                List<String> contentEncoding = reqHeaders.get("Content-Encoding");
                // TODO need to find out correct encoding based on q value - RFC 2616
                if (contentEncoding != null && contentEncoding.get(0).contains("gzip")) {
                    outputStream = new GZIPOutputStream(outputStream);
                }
            }
            httpConnection.connect();
        } catch (Exception ex) {
            throw new ClientTransportException(
                ClientMessages.localizableHTTP_CLIENT_FAILED(ex),ex);
        }

        return outputStream;
    }

    public void closeOutput() throws IOException {
        if (outputStream != null) {
            outputStream.close();
            outputStream = null;
        }
    }

    /**
     * Get the response from HTTP connection and prepare the input stream for response
     */
    public InputStream getInput() {
        // response processing

        InputStream in;
        try {
            in = readResponse();
            String contentEncoding = httpConnection.getContentEncoding();
            if (contentEncoding != null && contentEncoding.contains("gzip")) {
                in = new GZIPInputStream(in);
            }
        } catch (IOException e) {
            if (statusCode == HttpURLConnection.HTTP_NO_CONTENT
                || (isFailure
                && statusCode != HttpURLConnection.HTTP_INTERNAL_ERROR)) {
                try {
                    throw new ClientTransportException(ClientMessages.localizableHTTP_STATUS_CODE(
                        statusCode, httpConnection.getResponseMessage()));
                } catch (IOException ex) {
                    throw new ClientTransportException(ClientMessages.localizableHTTP_STATUS_CODE(
                        statusCode, ex));
                }
            }
            throw new ClientTransportException(ClientMessages.localizableHTTP_CLIENT_FAILED(e),e);
        }
        return in;
    }

    public Map<String, List<String>> getHeaders() {
        if (respHeaders != null) {
            return respHeaders;
        }
        respHeaders = new Headers();
        respHeaders.putAll(httpConnection.getHeaderFields());
        return respHeaders;
    }

    protected InputStream readResponse() throws IOException {
        return (isFailure
                ? httpConnection.getErrorStream()
                : httpConnection.getInputStream());
    }

    /*
     * Will throw an exception instead of returning 'false' if there is no
     * return message to be processed (i.e., in the case of an UNAUTHORIZED
     * response from the servlet or 404 not found)
     */
    /*package*/void checkResponseCode() {
        try {

            statusCode = httpConnection.getResponseCode();

            if ((httpConnection.getResponseCode()
                == HttpURLConnection.HTTP_INTERNAL_ERROR)) {
                isFailure = true;
                //added HTTP_ACCEPT for 1-way operations
            } else if (
                httpConnection.getResponseCode()
                    == HttpURLConnection.HTTP_UNAUTHORIZED) {

                // no soap message returned, so skip reading message and throw exception
                throw new ClientTransportException(
                    ClientMessages.localizableHTTP_CLIENT_UNAUTHORIZED(httpConnection.getResponseMessage()));
            } else if (
                httpConnection.getResponseCode()
                    == HttpURLConnection.HTTP_NOT_FOUND) {

                // no message returned, so skip reading message and throw exception
                throw new ClientTransportException(
                    ClientMessages.localizableHTTP_NOT_FOUND(httpConnection.getResponseMessage()));
            } else if (
                (statusCode == HttpURLConnection.HTTP_MOVED_TEMP) ||
                    (statusCode == HttpURLConnection.HTTP_MOVED_PERM)) {
                isFailure = true;

                if (!redirect || (redirectCount <= 0)) {
                    throw new ClientTransportException(
                        ClientMessages.localizableHTTP_STATUS_CODE(statusCode,getStatusMessage()));
                }
            } else if (
                statusCode < 200 || (statusCode >= 303 && statusCode < 500)) {
                throw new ClientTransportException(
                    ClientMessages.localizableHTTP_STATUS_CODE(statusCode,getStatusMessage()));
            } else if (statusCode >= 500) {
                isFailure = true;
            }
        } catch (IOException e) {
            throw new WebServiceException(e);
        }
        // Hack for now
        saveCookieAsNeeded();
    }

    String getStatusMessage() throws IOException {
        int statusCode = httpConnection.getResponseCode();
        String message = httpConnection.getResponseMessage();
        if (statusCode == HttpURLConnection.HTTP_CREATED
            || (statusCode >= HttpURLConnection.HTTP_MULT_CHOICE
            && statusCode != HttpURLConnection.HTTP_NOT_MODIFIED
            && statusCode < HttpURLConnection.HTTP_BAD_REQUEST)) {
            String location = httpConnection.getHeaderField("Location");
            if (location != null)
                message += " - Location: " + location;
        }
        return message;
    }

    protected void sendCookieAsNeeded() {
        Boolean shouldMaintainSessionProperty =
            (Boolean) context.invocationProperties.get(SESSION_MAINTAIN_PROPERTY);
        if (shouldMaintainSessionProperty != null && shouldMaintainSessionProperty) {
            cookieJar = (CookieJar) context.invocationProperties.get(HTTP_COOKIE_JAR);
            if (cookieJar == null) {
                cookieJar = new CookieJar();

                // need to store in binding's context so it is not lost
                context.proxy.getRequestContext().put(HTTP_COOKIE_JAR, cookieJar);
            }
            cookieJar.applyRelevantCookies(httpConnection);
        }
    }

    private void saveCookieAsNeeded() {
        if (cookieJar != null) {
            cookieJar.recordAnyCookies(httpConnection);
        }
    }


    private void createHttpConnection() throws IOException {

        // does the client want request redirection to occur
        String redirectProperty =
            (String) context.invocationProperties.get(REDIRECT_REQUEST_PROPERTY);
        if (redirectProperty != null) {
            if (redirectProperty.equalsIgnoreCase("false"))
                redirect = false;
        }

        checkEndpoints();

        httpConnection = (HttpURLConnection) endpoint.openConnection();
        if (httpConnection instanceof HttpsURLConnection) {
            https = true;

            boolean verification = false;
            // does the client want client hostname verification by the service
            String verificationProperty =
                (String) context.invocationProperties.get(HOSTNAME_VERIFICATION_PROPERTY);
            if (verificationProperty != null) {
                if (verificationProperty.equalsIgnoreCase("true"))
                    verification = true;
            }
            if (!verification) {
                ((HttpsURLConnection) httpConnection).setHostnameVerifier(new HttpClientVerifier());
            }

            // Set application's HostNameVerifier for this connection
            HostnameVerifier verifier =
                (HostnameVerifier) context.invocationProperties.get(JAXWSProperties.HOSTNAME_VERIFIER);
            if (verifier != null) {
                ((HttpsURLConnection) httpConnection).setHostnameVerifier(verifier);
            }

            // Set application's SocketFactory for this connection
            SSLSocketFactory sslSocketFactory =
                (SSLSocketFactory) context.invocationProperties.get(JAXWSProperties.SSL_SOCKET_FACTORY);
            if (sslSocketFactory != null) {
                ((HttpsURLConnection) httpConnection).setSSLSocketFactory(sslSocketFactory);
            }

        }


        writeBasicAuthAsNeeded(context, reqHeaders);

        // allow interaction with the web page - user may have to supply
        // username, password id web page is accessed from web browser
        httpConnection.setAllowUserInteraction(true);

        // enable input, output streams
        httpConnection.setDoOutput(true);
        httpConnection.setDoInput(true);

        String requestMethod = (String) context.invocationProperties.get(MessageContext.HTTP_REQUEST_METHOD);
        String method = (requestMethod != null) ? requestMethod : "POST";
        httpConnection.setRequestMethod(method);

        //this code or something similiar needs t be moved elsewhere for error checking
        /*if (context.invocationProperties.get(BindingProviderProperties.BINDING_ID_PROPERTY).equals(HTTPBinding.HTTP_BINDING)){
            method = (requestMethod != null)?requestMethod:method;            
        } else if
            (context.invocationProperties.get(BindingProviderProperties.BINDING_ID_PROPERTY).equals(SOAPBinding.SOAP12HTTP_BINDING) &&
            "GET".equalsIgnoreCase(requestMethod)) {
        }
       */     

        Integer reqTimeout = (Integer)context.invocationProperties.get(BindingProviderProperties.REQUEST_TIMEOUT);
        if (reqTimeout != null) {
            httpConnection.setReadTimeout(reqTimeout);
        }

        Integer connectTimeout = (Integer)context.invocationProperties.get(JAXWSProperties.CONNECT_TIMEOUT);
        if (connectTimeout != null) {
            httpConnection.setConnectTimeout(connectTimeout);
        }

        Integer chunkSize = (Integer)context.invocationProperties.get(JAXWSProperties.HTTP_CLIENT_STREAMING_CHUNK_SIZE);
        if (chunkSize != null) {
            httpConnection.setChunkedStreamingMode(chunkSize);
        }

        // set the properties on HttpURLConnection
        for (Map.Entry<String, List<String>> entry : reqHeaders.entrySet()) {
            httpConnection.addRequestProperty(entry.getKey(), entry.getValue().get(0));
        }
        httpConnection.addRequestProperty("User-Agent", RuntimeVersion.VERSION.toString());
    }

    public boolean isSecure() {
        return https;
    }

    //    private void redirectRequest(HttpURLConnection httpConnection, SOAPMessageContext context) {
//        String redirectEndpoint = httpConnection.getHeaderField("Location");
//        if (redirectEndpoint != null) {
//            httpConnection.disconnect();
//            invoke(redirectEndpoint, context);
//        } else
//            System.out.println("redirection Failed");
//    }

    private boolean checkForRedirect(int statusCode) {
        return (((statusCode == 301) || (statusCode == 302)) && redirect && (redirectCount-- > 0));
    }

    private void checkEndpoints() {
        if (!LAST_ENDPOINT.equalsIgnoreCase(endpoint.toString())) {
            redirectCount = START_REDIRECT_COUNT;
            LAST_ENDPOINT = endpoint.toString();
        }
    }


    private void writeBasicAuthAsNeeded(Packet context, Map<String, List<String>> reqHeaders) {
        String user = (String) context.invocationProperties.get(BindingProvider.USERNAME_PROPERTY);
        if (user != null) {
            String pw = (String) context.invocationProperties.get(BindingProvider.PASSWORD_PROPERTY);
            if (pw != null) {
                StringBuffer buf = new StringBuffer(user);
                buf.append(":");
                buf.append(pw);
                String creds = printBase64Binary(buf.toString().getBytes());
                reqHeaders.put("Authorization", Collections.singletonList("Basic "+creds));
            }
        }
    }

    private boolean requiresOutputStream() {
        return !(httpConnection.getRequestMethod().equalsIgnoreCase("GET") ||
                httpConnection.getRequestMethod().equalsIgnoreCase("HEAD") ||
                httpConnection.getRequestMethod().equalsIgnoreCase("DELETE"));
    }

    public @Nullable String getContentType() {
        return httpConnection.getContentType();
    }

    // overide default SSL HttpClientVerifier to always return true
    // effectively overiding Hostname client verification when using SSL
    private static class HttpClientVerifier implements HostnameVerifier {
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    }

    /**
     * HttpURLConnection.getOuputStream() returns sun.net.www.http.ChunkedOuputStream in chunked
     * streaming mode. If you call ChunkedOuputStream.write(byte[20MB], int, int), then the whole data
     * is kept in memory. This wraps the ChunkedOuputStream so that it writes only small
     * chunks.
     */
    private static final class WSChunkedOuputStream extends OutputStream {
        final OutputStream actual;
        final int chunkSize;

        WSChunkedOuputStream(OutputStream actual, int chunkSize) {
            this.actual = actual;
            this.chunkSize = chunkSize;
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            int sent = 0;
            while(sent < len) {
                int chunk = len-sent;
                if (chunk > chunkSize) {
                    chunk = chunkSize;
                }
                actual.write(b, off, chunk);
                off += chunk;
                sent += chunk;
            }
        }

        @Override
        public void write(byte b[]) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(int b) throws IOException {
            actual.write(b);
        }

        @Override
        public void flush() throws IOException {
            actual.flush();
        }

        @Override
        public void close() throws IOException {
            actual.close();
        }
    }

}

