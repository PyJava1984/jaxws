/*
 * $Id: ServletConnectionImpl.java,v 1.8 2005-09-04 02:18:42 jitu Exp $
 */

/*
* Copyright (c) 2005 Sun Microsystems, Inc.
* All rights reserved.
*/

package com.sun.xml.ws.transport.http.servlet;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.sun.pept.ept.EPTFactory;
import com.sun.xml.ws.transport.WSConnectionImpl;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * <code>com.sun.xml.ws.spi.runtime.WSConnection</code> used by 
 * WSServletDelegate, uses <code>javax.servlet.http.HttpServletRequest</code>
 * and <code>javax.servlet.http.HttpServletResponse</code>
 *
 * @author WS Development Team
 */
public class ServletConnectionImpl extends WSConnectionImpl {

    private HttpServletRequest request;
    private HttpServletResponse response;
    private int status;
    private Map<String,List<String>> requestHeaders;
    private Map<String,List<String>> responseHeaders;

    public ServletConnectionImpl(HttpServletRequest request,
        HttpServletResponse response) {
        this.request = request;
        this.response = response;
    }

    @Override
    public Map<String,List<String>> getHeaders() {
        if (requestHeaders == null) {
            requestHeaders = new HashMap<String, List<String>>();
            Enumeration enums = request.getHeaderNames();
            while (enums.hasMoreElements()) {
                String headerName = (String) enums.nextElement();
                String headerValue = request.getHeader(headerName);
                List<String> values = requestHeaders.get(headerName);
                if (values == null) {
                    values = new ArrayList<String>();
                    requestHeaders.put(headerName, values);
                }
                values.add(headerValue);
            }
        }       
        return requestHeaders;
    }
    
    /**
     * sets response headers.
     */
    @Override
    public void setHeaders(Map<String,List<String>> headers) {
        responseHeaders = headers;
    }
    
    @Override
    public void setStatus(int status) {
        this.status = status;
    }
    
    /**
     * sets HTTP status code
     */
    @Override
    public int getStatus() {
        if (status == 0) {
            status = HttpURLConnection.HTTP_OK;
        }
        return status;
    }
    
    @Override
    public InputStream getInput() {
        try {
            return request.getInputStream();
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
        return null;
    }
    
    @Override
    public OutputStream getOutput() {
        // write HTTP status code, and headers
        response.setStatus(getStatus());
        if (responseHeaders != null) {
            for(Map.Entry <String, List<String>> entry : responseHeaders.entrySet()) {
                String name = entry.getKey();
                List<String> values = entry.getValue();
                for(String value : values) {
                    response.setHeader(name, value);
                }
            }
        }
        try {
            outputStream = response.getOutputStream();
            return outputStream;
        } catch(IOException ioe) {
            ioe.printStackTrace();
        }
        return null;
    }

}
