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

package com.sun.xml.ws.message;

import com.sun.xml.ws.api.message.Attachment;
import com.sun.xml.ws.api.message.AttachmentSet;
import com.sun.xml.ws.encoding.MimeMultipartParser;
import com.sun.xml.ws.resources.EncodingMessages;

import javax.activation.DataHandler;
import javax.xml.bind.attachment.AttachmentUnmarshaller;
import javax.xml.ws.WebServiceException;

/**
 * Implementation of {@link AttachmentUnmarshaller} that uses
 * loads attachments from {@link AttachmentSet} directly.
 *
 * @author Vivek Pandey
 * @see MimeMultipartParser
 */
public final class AttachmentUnmarshallerImpl extends AttachmentUnmarshaller {

    private final AttachmentSet attachments;

    public AttachmentUnmarshallerImpl(AttachmentSet attachments) {
        this.attachments = attachments;
    }

    @Override
    public DataHandler getAttachmentAsDataHandler(String cid) {
        Attachment a = attachments.get(stripScheme(cid));
        if(a==null)
            throw new WebServiceException(EncodingMessages.NO_SUCH_CONTENT_ID(cid));
        return a.asDataHandler();
    }

    @Override
    public byte[] getAttachmentAsByteArray(String cid) {
        Attachment a = attachments.get(stripScheme(cid));
        if(a==null)
            throw new WebServiceException(EncodingMessages.NO_SUCH_CONTENT_ID(cid));
        return a.asByteArray();
    }

    /**
     * The CID reference has 'cid:' prefix, so get rid of it.
     */
    private String stripScheme(String cid) {
        if(cid.startsWith("cid:")) // work defensively, in case the input is wrong
            cid = cid.substring(4);
        return cid;
    }

    // TODO fix the hack
    // So that SAAJ registers DCHs for MIME types
    static {
        new com.sun.xml.messaging.saaj.soap.AttachmentPartImpl();
    }
}
