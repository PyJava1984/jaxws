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

/**
 * Transport implementations that work inside the single JVM.
 * Useful for testing.
 *
 * <p>
 * Transports implemented in this package work off the exploded war file
 * image in the file system &mdash; it should have the same file layout
 * that you deploy into, say, Tomcat. They then look for <tt>WEB-INF/sun-jaxws.xml</tt>
 * to determine what services are in the application, and then deploy
 * them in a servlet-like environment.
 *
 * <p>
 * This package comes with two transports. One is the legacy
 * {@link LocalTransportFactory "local" transport}, which effectively
 * deploys a new service instance every time you create a new proxy/dispatch.
 * This is not only waste of computation, but it prevents services of the same
 * application from talking with each other.
 *
 * <p>
 * {@link InVmTransportFactory The "in-vm" transport} is the modern version
 * of the local transport that fixes this problem. You first deploy a new
 * application by using {@link InVmServer},
 * {@link InVmServer#getAddress() obtain its address}, configure the JAX-WS RI
 * with that endpoint, then use that to talk to the running service.
 */
package com.sun.xml.ws.transport.local;