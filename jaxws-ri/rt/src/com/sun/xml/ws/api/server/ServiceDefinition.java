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

package com.sun.xml.ws.api.server;

import com.sun.istack.NotNull;
import com.sun.xml.ws.api.model.wsdl.WSDLModel;

/**
 * Root of the unparsed WSDL and other resources referenced from it.
 * This object represents the description of the service
 * that a {@link WSEndpoint} offers.
 *
 * <p>
 * A description consists of a set of {@link SDDocument}, which
 * each represents a single XML document that forms a part of the
 * descriptor (for example, WSDL might refer to separate schema documents,
 * or a WSDL might refer to another WSDL.)
 *
 * <p>
 * {@link ServiceDefinition} and its descendants are immutable
 * read-only objects. Once they are created, they always return
 * the same value.
 *
 * <h2>Expected Usage</h2>
 * <p>
 * This object is intended to be used for serving the descriptors
 * to remote clients (such as by MEX, or other protocol-specific
 * metadata query, such as HTTP GET with "?wsdl" query string.)
 *
 * <p>
 * This object is <b>NOT</b> intended to be used by other
 * internal components to parse them. For that purpose, use
 * {@link WSDLModel} instead.
 *
 * @author Kohsuke Kawaguchi
 */
public interface ServiceDefinition extends Iterable<SDDocument> {
    /**
     * Gets the "primary" {@link SDDocument} that represents a WSDL.
     *
     * <p>
     * This WSDL eventually refers to all the other {@link SDDocument}s.
     *
     * @return
     *      always non-null.
     */
    @NotNull SDDocument getPrimary();

    /**
     * Adds a filter that is called while writing {@link SDDocument}'s infoset. This
     * filter is applied to the all the other reachable {@link SDDocument}s.
     *
     * @param filter that is called while writing the document
     */
    void addFilter(@NotNull SDDocumentFilter filter);
}
