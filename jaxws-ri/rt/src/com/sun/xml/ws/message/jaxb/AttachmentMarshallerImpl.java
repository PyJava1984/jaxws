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

package com.sun.xml.ws.message.jaxb;

import com.sun.xml.ws.api.message.Attachment;
import com.sun.xml.ws.message.AttachmentSetImpl;
import com.sun.xml.ws.message.DataHandlerAttachment;

import javax.activation.DataHandler;
import javax.xml.bind.attachment.AttachmentMarshaller;
import javax.xml.ws.WebServiceException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.UUID;

/**
 * Implementation of {@link AttachmentMarshaller}, its used from JAXBMessage to marshall swaref type
 *
 * @author Vivek Pandey
 * @see JAXBMessage
 */
final class AttachmentMarshallerImpl extends AttachmentMarshaller {
    private AttachmentSetImpl attachments;

    public AttachmentMarshallerImpl(AttachmentSetImpl attachemnts) {
        this.attachments = attachemnts;
    }

    /**
     * Release a reference to user objects to avoid keeping it in memory.
     */
    void cleanup() {
        attachments = null;
    }

    public String addMtomAttachment(DataHandler data, String elementNamespace, String elementLocalName) {
        // We don't use JAXB for handling XOP
        throw new IllegalStateException();
    }

    public String addMtomAttachment(byte[] data, int offset, int length, String mimeType, String elementNamespace, String elementLocalName) {
        // We don't use JAXB for handling XOP
        throw new IllegalStateException();
    }

    public String addSwaRefAttachment(DataHandler data) {
        String cid = encodeCid(null);
        Attachment att = new DataHandlerAttachment(cid, data);
        attachments.add(att);
        cid = "cid:" + cid;
        return cid;
    }

    private String encodeCid(String ns) {
        String cid = "example.jaxws.sun.com";
        String name = UUID.randomUUID() + "@";
        if (ns != null && (ns.length() > 0)) {
            try {
                URI uri = new URI(ns);
                cid = uri.toURL().getHost();
            } catch (URISyntaxException e) {
                e.printStackTrace();
                return null;
            } catch (MalformedURLException e) {
                try {
                    cid = URLEncoder.encode(ns, "UTF-8");
                } catch (UnsupportedEncodingException e1) {
                    throw new WebServiceException(e);
                }
            }
        }
        return name + cid;
    }
}