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

/**
 * {@link Message} implementation for JAXB.
 *
 * <pre>
 * TODO:
 *      Because a producer of a message doesn't generally know
 *      when a message is consumed, it's difficult for
 *      the caller to do a proper instance caching. Perhaps
 *      there should be a layer around JAXBContext that does that?
 * </pre>
 */
package com.sun.xml.ws.sandbox.message.impl.jaxb;

import com.sun.xml.ws.api.message.Message;