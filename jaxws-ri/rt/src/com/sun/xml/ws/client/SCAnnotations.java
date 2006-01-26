package com.sun.xml.ws.client;

import com.sun.xml.ws.util.JAXWSUtils;

import javax.xml.namespace.QName;
import javax.xml.ws.Service;
import javax.xml.ws.WebEndpoint;
import javax.xml.ws.WebServiceClient;
import javax.xml.ws.WebServiceException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;

/**
 * Represents parsed {@link WebServiceClient} and {@link WebEndpoint}
 * annotations on a {@link Service}-derived class.
 *
 * @author Kohsuke Kawaguchi
 */
final class SCAnnotations {
    SCAnnotations(final Class<?> sc) {
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                WebServiceClient wsc =sc.getAnnotation(WebServiceClient.class);
                if(wsc==null)
                    throw new WebServiceException("Service Interface Annotations required, exiting...");

                String name = wsc.name();
                String tns = wsc.targetNamespace();
                serviceQName = new QName(tns, name);
                try {
                    wsdlLocation = JAXWSUtils.getFileOrURL(wsc.wsdlLocation());
                } catch (IOException e) {
                    // TODO: report a reasonable error message
                    throw new WebServiceException(e);
                }

                for (Method method : sc.getDeclaredMethods()) {
                    WebEndpoint webEndpoint = method.getAnnotation(WebEndpoint.class);
                    if (webEndpoint != null) {
                        String endpointName = webEndpoint.name();
                        QName portQName = new QName(tns, endpointName);
                        portQNames.add(portQName);
                    }
                    Class<?> seiClazz = method.getReturnType();
                    if (seiClazz!=void.class) {
                        classes.add(seiClazz);
                    }
                }

                return null;
            }
        });
    }

    QName serviceQName;
    final ArrayList<QName> portQNames = new ArrayList<QName>();
    final ArrayList<Class> classes = new ArrayList<Class>();
    URL wsdlLocation;
}