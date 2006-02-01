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

package com.sun.xml.ws.transport.http.client;

import com.sun.xml.ws.api.message.MessageProperties;
import static com.sun.xml.ws.client.BindingProviderProperties.*;
import com.sun.xml.ws.client.ClientTransportException;
import com.sun.xml.ws.transport.WSConnectionImpl;
import com.sun.xml.ws.util.ByteArrayBuffer;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;
import javax.xml.soap.MimeHeaders;
import static javax.xml.ws.BindingProvider.SESSION_MAINTAIN_PROPERTY;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;

/**
 * @author WS Development Team
 */
public class HttpClientTransport extends WSConnectionImpl {

    private static String LAST_ENDPOINT = "";
    private static boolean redirect = true;
    private static final int START_REDIRECT_COUNT = 3;
    private static int redirectCount = START_REDIRECT_COUNT;
    int statusCode;
    private Map<String, List<String>> respHeaders = null;

    public HttpClientTransport(OutputStream logStream, MessageProperties properties) {
        _logStream = logStream;
        endpoint = properties.endpointAddress;
        context = properties;
    }

    /**
     * Prepare the stream for HTTP request
     */
    @Override
    public OutputStream getOutput() {
        try {
            httpConnection = createHttpConnection(endpoint, context);
            cookieJar = sendCookieAsNeeded();

            // how to incorporate redirect processing: message dispatcher does not seem to tbe right place
            if (!httpConnection.getRequestMethod().equalsIgnoreCase("GET"))
                outputStream = httpConnection.getOutputStream();
            //if use getOutputStream method set as "POST"
            //but for "Get" request no need to get outputStream
            connectForResponse();

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new ClientTransportException("http.client.failed",ex);
        }

        return outputStream;
    }

    /**
     * Get the response from HTTP connection and prepare the input stream for response
     */
    @Override
    public InputStream getInput() {
        // response processing

        InputStream in;
        try {
            in = readResponse();
        } catch (IOException e) {
            if (statusCode == HttpURLConnection.HTTP_NO_CONTENT
                || (isFailure
                && statusCode != HttpURLConnection.HTTP_INTERNAL_ERROR)) {
                try {
                    throw new ClientTransportException("http.status.code",
                        statusCode, httpConnection.getResponseMessage());
                } catch (IOException ex) {
                    throw new ClientTransportException("http.status.code",
                        statusCode, ex);
                }
            }
            throw new ClientTransportException("http.client.failed",
                e.getMessage());
        }
        httpConnection = null;

        return in;
    }

    @Override
    public OutputStream getDebug() {
        return _logStream;
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        if (respHeaders != null) {
            return respHeaders;
        }
        try {
            isFailure = checkResponseCode();

            respHeaders = collectResponseMimeHeaders();

            saveCookieAsNeeded(cookieJar);
            setHeaders(respHeaders);

            return respHeaders;
        } catch (IOException e) {
            if (statusCode == HttpURLConnection.HTTP_NO_CONTENT
                || (isFailure
                && statusCode != HttpURLConnection.HTTP_INTERNAL_ERROR)) {
                try {
                    throw new ClientTransportException("http.status.code",
                        new Object[]{
                            statusCode,
                            httpConnection.getResponseMessage()});
                } catch (IOException ex) {
                    throw new ClientTransportException("http.status.code",
                        new Object[]{
                            statusCode,
                            ex});
                }
            }
            throw new ClientTransportException("http.client.failed",
                e.getMessage());
        }

    }

//    public void invoke(String endpoint, SOAPMessageContext context)
//            throws ClientTransportException {

//        try {
//            int statusCode = httpConnection.getResponseCode();
//
//            //http URL redirection does not redirect http requests
//            //to an https endpoint probably due to a bug in the jdk
//            //or by intent - to workaround this if an error code
//            //of HTTP_MOVED_TEMP or HTTP_MOVED_PERM is received then
//            //the jaxws client will reinvoke the original request
//            //to the new endpoint - kw bug 4890118
//            if (checkForRedirect(statusCode)) {
//                redirectRequest(httpConnection, context);
//                return;
//            }
//    }

    protected InputStream readResponse()
        throws IOException {
        InputStream contentIn =
            (isFailure
                ? httpConnection.getErrorStream()
                : httpConnection.getInputStream());

        ByteArrayBuffer bab = new ByteArrayBuffer();
        if (contentIn != null) { // is this really possible?
            bab.write(contentIn);
            bab.close();
        }

        int length =
            httpConnection.getContentLength() == -1
                ? bab.size()
                : httpConnection.getContentLength();

        return bab.newInputStream(0, length);
    }

    public Map<String, List<String>> collectResponseMimeHeaders() {

       /* MimeHeaders mimeHeaders = new MimeHeaders();
        for (int i = 1; ; ++i) {
            String key = httpConnection.getHeaderFieldKey(i);
            if (key == null) {
                break;
            }
            String value = httpConnection.getHeaderField(i);
            try {
                mimeHeaders.addHeader(key, value);
            } catch (IllegalArgumentException e) {
                // ignore headers that are illegal in MIME
            }
        }

        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        for (Iterator iter = mimeHeaders.getAllHeaders(); iter.hasNext();) {
            MimeHeader header = (MimeHeader)iter.next();
            List<String> h = new ArrayList<String>();
            h.add(header.getValue());
            headers.put (header.getName (), h);
        }
        return headers;
        */
        return httpConnection.getHeaderFields();
    }

    public MimeHeaders collectMimeHeaders() {

        MimeHeaders mimeHeaders = new MimeHeaders();
        for (int i = 1; ; ++i) {
            String key = httpConnection.getHeaderFieldKey(i);
            if (key == null) {
                break;
            }
            String value = httpConnection.getHeaderField(i);
            try {
                mimeHeaders.addHeader(key, value);
            } catch (IllegalArgumentException e) {
                // ignore headers that are illegal in MIME
            }
             mimeHeaders.addHeader("Content-Type", "text/xml");
             mimeHeaders.addHeader("Content-Transfer-Encoding", "binary");
        }
        return mimeHeaders;
       /* Map<String, List<String>> headers = new HashMap<String, List<String>>();
        for (Iterator iter = mimeHeaders.getAllHeaders(); iter.hasNext();) {
            MimeHeader header = (MimeHeader)iter.next();
            List<String> h = new ArrayList<String>();
            h.add(header.getValue());
            headers.put (header.getName (), h);
        }
        return headers;
        */
        //return httpConnection.getHeaderFields();
    }

    protected void connectForResponse()
        throws IOException {

        httpConnection.connect();
    }

    /*
     * Will throw an exception instead of returning 'false' if there is no
     * return message to be processed (i.e., in the case of an UNAUTHORIZED
     * response from the servlet or 404 not found)
     */
    protected boolean checkResponseCode()
        throws IOException {
        boolean isFailure = false;
        try {

            statusCode = httpConnection.getResponseCode();
            setStatus(statusCode);

            if ((httpConnection.getResponseCode()
                == HttpURLConnection.HTTP_INTERNAL_ERROR)) {
                isFailure = true;
                //added HTTP_ACCEPT for 1-way operations
            } else if (
                httpConnection.getResponseCode()
                    == HttpURLConnection.HTTP_UNAUTHORIZED) {

                // no soap message returned, so skip reading message and throw exception
                throw new ClientTransportException("http.client.unauthorized",
                    httpConnection.getResponseMessage());
            } else if (
                httpConnection.getResponseCode()
                    == HttpURLConnection.HTTP_NOT_FOUND) {

                // no message returned, so skip reading message and throw exception
                throw new ClientTransportException("http.not.found",
                    httpConnection.getResponseMessage());
            } else if (
                (statusCode == HttpURLConnection.HTTP_MOVED_TEMP) ||
                    (statusCode == HttpURLConnection.HTTP_MOVED_PERM)) {
                isFailure = true;

                if (!redirect || (redirectCount <= 0)) {
                    throw new ClientTransportException("http.status.code",
                        new Object[]{
                            statusCode,
                            getStatusMessage(httpConnection)});
                }
            } else if (
                statusCode < 200 || (statusCode >= 303 && statusCode < 500)) {
                throw new ClientTransportException("http.status.code",
                    new Object[]{
                        statusCode,
                        getStatusMessage(httpConnection)});
            } else if (statusCode >= 500) {
                isFailure = true;
            }
        } catch (IOException e) {
            // on JDK1.3.1_01, we end up here, but then getResponseCode() succeeds!
//            if (httpConnection.getResponseCode()
//                    == HttpURLConnection.HTTP_INTERNAL_ERROR) {
//                isFailure = true;
//            } else {
//                throw e;
//            }
        }

        return isFailure;
    }

    protected String getStatusMessage(HttpURLConnection httpConnection)
        throws IOException {
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

    protected CookieJar sendCookieAsNeeded() {
        Boolean shouldMaintainSessionProperty =
            (Boolean) context.otherProperties.get(SESSION_MAINTAIN_PROPERTY);
        if (shouldMaintainSessionProperty == null) {
            return null;
        }
        if (shouldMaintainSessionProperty) {
            CookieJar cookieJar = (CookieJar) context.otherProperties.get(HTTP_COOKIE_JAR);
            if (cookieJar == null) {
                cookieJar = new CookieJar();

                // need to store in binding's context so it is not lost
                context.proxy.getRequestContext().put(HTTP_COOKIE_JAR, cookieJar);
            }
            cookieJar.applyRelevantCookies(httpConnection);
            return cookieJar;
        } else {
            return null;
        }
    }

    protected void saveCookieAsNeeded(CookieJar cookieJar) {
        if (cookieJar != null) {
            cookieJar.recordAnyCookies(httpConnection);
        }
    }

    protected HttpURLConnection createHttpConnection(String endpoint,
                                                     MessageProperties context)
            throws IOException {

        boolean verification = false;
        // does the client want client hostname verification by the service
        String verificationProperty =
            (String) context.otherProperties.get(HOSTNAME_VERIFICATION_PROPERTY);
        if (verificationProperty != null) {
            if (verificationProperty.equalsIgnoreCase("true"))
                verification = true;
        }

        // does the client want request redirection to occur
        String redirectProperty =
            (String) context.otherProperties.get(REDIRECT_REQUEST_PROPERTY);
        if (redirectProperty != null) {
            if (redirectProperty.equalsIgnoreCase("false"))
                redirect = false;
        }

        checkEndpoints(endpoint);

        HttpURLConnection httpConnection = createConnection(endpoint);

        if (!verification) {
            // for https hostname verification  - turn off by default
            if (httpConnection instanceof HttpsURLConnection) {
                ((HttpsURLConnection) httpConnection).setHostnameVerifier(new HttpClientVerifier());
            }
        }

        // allow interaction with the web page - user may have to supply
        // username, password id web page is accessed from web browser

        httpConnection.setAllowUserInteraction(true);
        // enable input, output streams
        httpConnection.setDoOutput(true);
        httpConnection.setDoInput(true);
        // the soap message is always sent as a Http POST
        // HTTP Get is disallowed by BP 1.0
        // needed for XML/HTTPBinding and SOAP12Binding
        // for xml/http binding other methods are allowed.
        // for Soap 1.2 "GET" is allowed.
        String method = "POST";
        /*
        String requestMethod = (String) context.get(MessageContext.HTTP_REQUEST_METHOD);
        if (context.get(BindingProviderProperties.BINDING_ID_PROPERTY).equals(HTTPBinding.HTTP_BINDING)){
            method = (requestMethod != null)?requestMethod:method;            
        } else if
            (context.get(BindingProviderProperties.BINDING_ID_PROPERTY).equals(SOAPBinding.SOAP12HTTP_BINDING) &&
            "GET".equalsIgnoreCase(requestMethod)) {
            method = (requestMethod != null)?requestMethod:method;
        }
         */
        httpConnection.setRequestMethod(method);

        // set the properties on HttpURLConnection
        for (Map.Entry entry : super.getHeaders().entrySet()) {
            httpConnection.addRequestProperty((String) entry.getKey(), ((List<String>) entry.getValue()).get(0));
        }
        
        return httpConnection;
    }

    private java.net.HttpURLConnection createConnection(String endpoint)
        throws IOException {
        return (HttpURLConnection) new URL(endpoint).openConnection();
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

    private void checkEndpoints(String currentEndpoint) {
        if (!LAST_ENDPOINT.equalsIgnoreCase(currentEndpoint)) {
            redirectCount = START_REDIRECT_COUNT;
            LAST_ENDPOINT = currentEndpoint;
        }
    }

    // overide default SSL HttpClientVerifier to always return true
    // effectively overiding Hostname client verification when using SSL
    static class HttpClientVerifier implements HostnameVerifier {
        public boolean verify(String s, SSLSession sslSession) {
            return true;
        }
    }

    HttpURLConnection httpConnection = null;
    String endpoint = null;
    MessageProperties context = null;
    CookieJar cookieJar = null;
    boolean isFailure = false;
    OutputStream _logStream = null;
}

