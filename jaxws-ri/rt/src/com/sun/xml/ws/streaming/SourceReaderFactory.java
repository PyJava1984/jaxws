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

package com.sun.xml.ws.streaming;

import com.sun.xml.ws.util.FastInfosetUtil;
import com.sun.xml.ws.util.xml.XmlUtil;

import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.net.URL;

/**
 * @author Santiago.PericasGeertsen@sun.com
 */
public class SourceReaderFactory {
    
    /**
     * FI FastInfosetSource class.
     */
    static Class fastInfosetSourceClass;
    
    /**
     * FI <code>StAXDocumentSerializer.setEncoding()</code> method via reflection.
     */
    static Method fastInfosetSource_getInputStream;

    static {
        // Use reflection to avoid static dependency with FI jar
        try {
            fastInfosetSourceClass =
                Class.forName("org.jvnet.fastinfoset.FastInfosetSource");
            fastInfosetSource_getInputStream = 
                fastInfosetSourceClass.getMethod("getInputStream");
        } 
        catch (Exception e) {
            fastInfosetSourceClass = null;
        }
    }

    public static XMLStreamReader createSourceReader(Source source, boolean rejectDTDs) {
        return createSourceReader(source, rejectDTDs, null);
    }
    
    public static XMLStreamReader createSourceReader(Source source, boolean rejectDTDs, String charsetName) {
        try {
            if (source instanceof StreamSource) {
                StreamSource streamSource = (StreamSource) source;
                InputStream is = streamSource.getInputStream();

                if (is != null) {
                    // Wrap input stream in Reader if charset is specified
                    if (charsetName != null) {
                        return XMLStreamReaderFactory.create(
                            source.getSystemId(), new InputStreamReader(is, charsetName), rejectDTDs);                    
                    }
                    else {
                        return XMLStreamReaderFactory.create(
                            source.getSystemId(), is, rejectDTDs);
                    }
                }
                else {
                    Reader reader = streamSource.getReader();
                    if (reader != null) {
                        return XMLStreamReaderFactory.create(
                            source.getSystemId(), reader, rejectDTDs);
                    }
                    else {
                        return XMLStreamReaderFactory.create(
                            source.getSystemId(), new URL(source.getSystemId()).openStream(), rejectDTDs );
                    }
                }
            }
            else if (source.getClass() == fastInfosetSourceClass) {
                return FastInfosetUtil.createFIStreamReader((InputStream)
                    fastInfosetSource_getInputStream.invoke(source));
            }
            else if (source instanceof DOMSource) {
                DOMStreamReader dsr =  new DOMStreamReader();
                dsr.setCurrentNode(((DOMSource) source).getNode());
                return dsr;
            }
            else if (source instanceof SAXSource) {
                // TODO: need SAX to StAX adapter here -- Use transformer for now
                Transformer tx =  XmlUtil.newTransformer();
                DOMResult domResult = new DOMResult();
                tx.transform(source, domResult);
                return createSourceReader(
                    new DOMSource(domResult.getNode()),
                    rejectDTDs);
            }
            else {
                throw new XMLReaderException("sourceReader.invalidSource",
                        source.getClass().getName());
            }        
        }
        catch (Exception e) {
            throw new XMLReaderException(e);
        }
    }

}
