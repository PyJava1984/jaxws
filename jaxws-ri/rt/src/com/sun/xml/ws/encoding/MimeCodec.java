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

package com.sun.xml.ws.encoding;

import com.sun.xml.messaging.saaj.packaging.mime.util.OutputUtil;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.message.Attachment;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.pipe.Codec;
import com.sun.xml.ws.api.pipe.ContentType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.ReadableByteChannel;
import java.util.UUID;

/**
 * {@link Codec}s that uses the MIME multipart as the underlying format.
 *
 * <p>
 * When the runtime needs to dynamically choose a {@link Codec}, and
 * when there are more than one {@link Codec}s that use MIME multipart,
 * it is often impossible to determine the right {@link Codec} unless
 * you parse the multipart message to some extent.
 *
 * <p>
 * By having all such {@link Codec}s extending from this class,
 * the "sniffer" can decode a multipart message partially, and then
 * pass the partial parse result to the ultimately-responsible {@link Codec}.
 * This improves the performance.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class MimeCodec implements Codec {
    public static final String MULTIPART_RELATED_MIME_TYPE = "multipart/related";
    
    private String boundary;
    private String messageContentType;
    private boolean hasAttachments;
    protected Codec rootCodec;
    protected final SOAPVersion version;

    protected MimeCodec(SOAPVersion version) {
        this.version = version;
    }
    
    public String getMimeType() {
        return MULTIPART_RELATED_MIME_TYPE;
    }

    // TODO: preencode String literals to byte[] so that they don't have to
    // go through char[]->byte[] conversion at runtime.
    public ContentType encode(Packet packet, OutputStream out) throws IOException {
        Message msg = packet.getMessage();
        if (msg == null) {
            return null;
        }

        if (hasAttachments) {
            OutputUtil.writeln("--"+boundary, out);
            OutputUtil.writeln("Content-Type: " + rootCodec.getMimeType(), out);
            OutputUtil.writeln(out);
        }
        ContentType primaryCt = rootCodec.encode(packet, out);

        if (hasAttachments) {
            OutputUtil.writeln(out);
            // Encode all the attchments
            for (Attachment att : msg.getAttachments()) {
                OutputUtil.writeln("--"+boundary, out);
                //SAAJ's AttachmentPart.getContentId() returns content id already enclosed with
                //angle brackets. For now put angle bracket only if its not there
                String cid = att.getContentId();
                if(cid != null && cid.length() >0 && cid.charAt(0) != '<')
                    cid = '<' + cid + '>';
                OutputUtil.writeln("Content-Id:" + cid, out);
                OutputUtil.writeln("Content-Type: " + att.getContentType(), out);
                OutputUtil.writeln("Content-Transfer-Encoding: binary", out);
                OutputUtil.writeln(out);                    // write \r\n
                att.writeTo(out);
                OutputUtil.writeln(out);                    // write \r\n
            }
            OutputUtil.writeAsAscii("--"+boundary, out);
            OutputUtil.writeAsAscii("--", out);
        }
        // TODO not returing correct multipart/related type(no boundary)
        return hasAttachments ? new ContentTypeImpl(messageContentType, packet.soapAction, null) : primaryCt;
    }
    
    public ContentType getStaticContentType(Packet packet) {
        Message msg = packet.getMessage();
        hasAttachments = !msg.getAttachments().isEmpty();

        if (hasAttachments) {
            boundary = "uuid:" + UUID.randomUUID().toString();
            String boundaryParameter = "boundary=\"" + boundary + "\"";
            // TODO use primaryEncoder to get type
            messageContentType =  MULTIPART_RELATED_MIME_TYPE + 
                    "; type=\"" + rootCodec.getMimeType() + "\"; " +
                    boundaryParameter;
            return new ContentTypeImpl(messageContentType, packet.soapAction, null);
        } else {
            return rootCodec.getStaticContentType(packet);
        }
    }

    /**
     * Copy constructor.
     */
    protected MimeCodec(MimeCodec that) {
        this.version = that.version;
    }

    public void decode(InputStream in, String contentType, Packet packet) throws IOException {
        MimeMultipartParser parser = new MimeMultipartParser(in, contentType);
        decode(parser,packet);
    }

    public void decode(ReadableByteChannel in, String contentType, Packet packet) {
        throw new UnsupportedOperationException();
    }

    /**
     * Parses a {@link Packet} from a {@link MimeMultipartParser}.
     */
    protected abstract void decode(MimeMultipartParser mpp, Packet packet) throws IOException;

    public abstract MimeCodec copy();
}