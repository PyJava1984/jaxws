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

package com.sun.tools.ws;

import com.sun.istack.tools.MaskingClassLoader;
import com.sun.istack.tools.ParallelWorldClassLoader;
import com.sun.tools.xjc.api.util.ToolsJarNotFoundException;

import javax.xml.ws.Service;
import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Invokes JAX-WS tools in a special class loader that can pick up APT classes,
 * even if it's not available in the tool launcher classpath.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Invoker {
    static int invoke(String mainClass, String[] args) throws Throwable {
        // use the platform default proxy if available.
        // see sun.net.spi.DefaultProxySelector for details.
        try {
            System.setProperty("java.net.useSystemProxies","true");
        } catch (SecurityException e) {
            // failing to set this property isn't fatal
        }

        ClassLoader oldcc = Thread.currentThread().getContextClassLoader();
        try {
            ClassLoader cl = createClassLoader(Invoker.class.getClassLoader());
            Thread.currentThread().setContextClassLoader(cl);

            Class compileTool = cl.loadClass(mainClass);
            Constructor ctor = compileTool.getConstructor(OutputStream.class);
            Object tool = ctor.newInstance(System.out);
            Method runMethod = compileTool.getMethod("run",String[].class);
            boolean r = (Boolean)runMethod.invoke(tool,new Object[]{args});
            return r ? 0 : 1;
        } catch (ToolsJarNotFoundException e) {
            System.err.println(e.getMessage());
        } catch (InvocationTargetException e) {
            throw e.getCause();
        } finally {
            Thread.currentThread().setContextClassLoader(oldcc);
        }

        return -1;
    }

    /**
     * Creates a classloader that can load JAXB/WS 2.1 API and tools.jar,
     * and then return a classloader that can RI classes, which can see all those APIs and tools.jar.  
     */
    public static ClassLoader createClassLoader(ClassLoader cl) throws ClassNotFoundException, MalformedURLException, ToolsJarNotFoundException {

        URL[] urls = findIstackAPIs(cl);
        if(urls.length==0)
            return cl;  // we seem to be able to load everything already. no need for the hack

        List<String> mask = new ArrayList<String>(Arrays.asList(maskedPackages));
        if(urls.length>1) {
            // we need to load 2.1 API from side. so add them to the mask
            mask.add("javax.xml.bind.");
            mask.add("javax.xml.ws.");
        }

        // first create a protected area so that we load JAXB/WS 2.1 API
        // and everything that depends on them inside
        cl = new MaskingClassLoader(cl,mask);

        // then this classloader loads the API and tools.jar
        cl = new URLClassLoader(urls, cl);

        // finally load the rest of the RI. The actual class files are loaded from ancestors
        cl = new ParallelWorldClassLoader(cl,"");

        return cl;
    }

    /**
     * Creates a classloader for loading JAXB/WS 2.1 jar and tools.jar
     */
    private static URL[] findIstackAPIs(ClassLoader cl) throws ClassNotFoundException, MalformedURLException, ToolsJarNotFoundException {
        List<URL> urls = new ArrayList<URL>();

        if(Service.class.getClassLoader()==null) {
            // JAX-WS API is loaded from bootstrap classloader
            URL res = cl.getResource("javax/xml/ws/EndpointReference.class");
            if(res==null)
                throw new ClassNotFoundException("There's no JAX-WS 2.1 API in the classpath");
            urls.add(ParallelWorldClassLoader.toJarUrl(res));

            res = cl.getResource("javax/xml/bind/annotation/XmlSeeAlso.class");
            if(res==null)
                throw new ClassNotFoundException("There's no JAXB 2.1 API in the classpath");
            urls.add(ParallelWorldClassLoader.toJarUrl(res));
        }

        try {
            Class.forName("com.sun.tools.javac.Main",false,cl);
            Class.forName("com.sun.tools.apt.Main",false,cl);
            // we can already load them in the parent class loader.
            // so no need to look for tools.jar.
            // this happens when we are run inside IDE/Ant, or
            // in Mac OS.
        } catch (ClassNotFoundException e) {
            // otherwise try to find tools.jar
            File jreHome = new File(System.getProperty("java.home"));
            File toolsJar = new File( jreHome.getParent(), "lib/tools.jar" );

            if (!toolsJar.exists()) {
                throw new ToolsJarNotFoundException(toolsJar);
            }
            urls.add(toolsJar.toURL());
        }

        return urls.toArray(new URL[urls.size()]);
    }

    /**
     * The list of package prefixes we want the
     * {@link MaskingClassLoader} to prevent the parent
     * classLoader from loading
     */
    public static String[] maskedPackages = new String[]{
        "com.sun.istack.tools.",
        "com.sun.tools.jxc.",
        "com.sun.tools.xjc.",
        "com.sun.tools.ws.",
        "com.sun.codemodel.",
        "com.sun.relaxng.",
        "com.sun.xml.xsom.",
        "com.sun.xml.bind."
    };
}
