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
package com.sun.xml.ws.api.pipe;

import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.WSBinding;

import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.channels.ReadableByteChannel;

/**
 * Encodes a {@link Message} (its XML infoset and attachments) to a sequence of bytes.
 *
 * <p>
 * This interface provides pluggability for different ways of encoding XML infoset,
 * such as plain XML (plus MIME attachments), XOP, and FastInfoset.
 *
 * <p>
 * Transport usually needs a MIME content type of the encoding, so the {@link Codec}
 * interface is designed to return this information. However, for some encoding
 * (such as XOP), the encoding may actually change based on the actual content of
 * {@link Message}, therefore the codec returns the content type as a result of encoding.
 *
 * <p>
 * {@link Codec} does not produce transport-specific information, such as HTTP headers.
 *
 * <p>
 * {@link Codec} is a non-reentrant object, meaning no two threads
 * can concurrently invoke the decode method. This allows the implementation
 * to easily reuse parser objects (as instance variables), which are costly otherwise.
 *
 *
 * <p>
 * {@link WSBinding} determines the {@link Codec}. See {@link WSBinding#createCodec()}.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Codec {

    /**
     * If the MIME content-type of the encoding is known statically
     * then this method returns it.
     *
     * <p>
     * Transports often need to write the content type before it writes
     * the message body, and since the encode method returns the content type
     * after the body is written, it requires a buffering.
     *
     * For those {@link Codec}s that always use a constant content type,
     * This method allows a transport to streamline the write operation.
     *
     * @return
     *      null if the content-type can't be determined in short of
     *      encodin the packet. Otherwise content type for this {@link Packet},
     *      such as "application/xml".
     */
    ContentType getStaticContentType(Packet packet);

    /**
     * Encodes an XML infoset portion of the {@link Message}
     * (from &lt;soap:Envelope> to &lt;/soap:Envelope>).
     *
     * <p>
     * Internally, this method is most likely invoke {@link Message#writeTo(XMLStreamWriter)}
     * to turn the message into infoset.
     *
     * @param packet
     * @param out
     *      Must not be null. The caller is responsible for closing the stream,
     *      not the callee.
     *
     * @return
     *      The MIME content type of the encoded message (such as "application/xml").
     *      This information is often ncessary by transport.
     *
     * @throws IOException
     *      if a {@link OutputStream} throws {@link IOException}.
     */
    ContentType encode( Packet packet, OutputStream out ) throws IOException;

    /**
     * The version of {@link #encode(Packet,OutputStream)}
     * that writes to NIO {@link ByteBuffer}.
     *
     * <p>
     * TODO: for the convenience of implementation, write
     * an adapter that wraps {@link WritableByteChannel} to {@link OutputStream}.
     */
    ContentType encode( Packet packet, WritableByteChannel buffer );

    /*
     * The following methods need to be documented and implemented.
     *
     * Such methods will be used by a client side
     * transport pipe that implements the ClientEdgePipe.
     *
    String encode( InputStreamMessage message, OutputStream out ) throws IOException;
    String encode( InputStreamMessage message, WritableByteChannel buffer );
    */

    /**
     * Creates a copy of this {@link Codec}.
     *
     * <p>
     * Since {@link Codec} instance is not re-entrant, the caller
     * who needs to encode two {@link Message}s simultaneously will
     * want to have two {@link Codec} instances. That's what this
     * method produces.
     *
     * <h3>Implentation Note</h3>
     * <p>
     * Note that this method might be invoked by one thread while
     * another thread is executing one of the {@link #encode} methods.
     * <!-- or otherwise you'd always have to maintain one idle copy -->
     * <!-- just so that you can make copies from -->
     * This should be OK because you'll be only copying things that
     * are thread-safe, and creating new ones for thread-unsafe resources,
     * but please let us know if this contract is difficult.
     *
     * @return
     *      always non-null valid {@link Codec} that performs
     *      the encoding work in the same way --- that is, if you
     *      copy an FI codec, you'll get another FI codec.
     *
     *      <p>
     *      Once copied, two {@link Codec}s may be invoked from
     *      two threads concurrently; therefore, they must not share
     *      any state that requires isolation (such as temporary buffer.)
     *
     *      <p>
     *      If the {@link Codec} implementation is already
     *      re-entrant and multi-thread safe to begin with,
     *      then this method may simply return <tt>this</tt>.
     */
    Codec copy();

    /**
     * Reads bytes from {@link InputStream} and constructs a {@link Message}.
     *
     * <p>
     * The design encourages lazy decoding of a {@link Message}, where
     * a {@link Message} is returned even before the whole message is parsed,
     * and additional parsing is done as the {@link Message} body is read along.
     * A {@link Decoder} is most likely have its own implementation of {@link Message}
     * for this purpose.
     *
     * @param in
     *      the data to be read into a {@link Message}. The transport would have
     *      read any transport-specific header before it passes an {@link InputStream},
     *      and {@link InputStream} is expected to be read until EOS. Never null.
     *
     *      <p>
     *      Some transports, such as SMTP, may 'encode' data into another format
     *      (such as uuencode, base64, etc.) It is the caller's responsibility to
     *      'decode' these transport-level encoding before it passes data into
     *      {@link Decoder}.
     *
     * @param contentType
     *      The MIME content type (like "application/xml") of this byte stream.
     *      Thie text includes all the sub-headers of the content-type header. Therefore,
     *      in more complex case, this could be something like
     *      <tt>multipart/related; boundary="--=_outer_boundary"; type="multipart/alternative"</tt>.
     *      This parameter must not be null.
     *
     * @param response
     *      The parsed {@link Message} will be set to this {@link Packet}.
     *      {@link Decoder} may add additional properties to this {@link Packet}.
     *      On a successful method completion, a {@link Packet} must contain a
     *      {@link Message}.
     *
     * @throws IOException
     *      if {@link InputStream} throws an exception.
     */
    void decode( InputStream in, String contentType, Packet response ) throws IOException;

    /**
     *
     * @see #decode(InputStream, String, Packet)
     */
    void decode( ReadableByteChannel in, String contentType, Packet response );

    /*
     * The following methods need to be documented and implemented.
     *
     * Such methods will be used by a server side
     * transport pipe that can support the invocation of methods on a
     * ServerEdgePipe.
     *
    XMLStreamReaderMessage decode( InputStream in, String contentType ) throws IOException;
    XMLStreamReaderMessage decode( ReadableByteChannel in, String contentType );
    */
}
