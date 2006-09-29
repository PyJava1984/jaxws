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
package com.sun.xml.ws.api.message;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.WebServiceContext;
import javax.xml.ws.handler.LogicalMessageContext;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.handler.soap.SOAPMessageContext;

import com.sun.xml.ws.addressing.WsaPipeHelper;
import com.sun.xml.ws.api.EndpointAddress;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.addressing.AddressingVersion;
import com.sun.xml.ws.api.addressing.WSEndpointReference;
import com.sun.xml.ws.api.model.wsdl.WSDLOperation;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.ws.api.pipe.Pipe;
import com.sun.xml.ws.api.server.TransportBackChannel;
import com.sun.xml.ws.api.server.WSEndpoint;
import com.sun.xml.ws.api.server.WebServiceContextDelegate;
import com.sun.xml.ws.client.BindingProviderProperties;
import com.sun.xml.ws.client.ContentNegotiation;
import com.sun.xml.ws.client.HandlerConfiguration;
import com.sun.xml.ws.client.ResponseContext;
import com.sun.xml.ws.message.StringHeader;
import com.sun.xml.ws.message.RelatesToHeader;
import com.sun.xml.ws.util.DistributedPropertySet;
import com.sun.istack.Nullable;

/**
 * Represents a container of a {@link Message}.
 *
 * <h2>What is a {@link Packet}?</h2>
 * <p>
 * A packet can be thought of as a frame/envelope/package that wraps
 * a {@link Message}. A packet keeps track of optional metadata (properties)
 * about a {@link Message} that doesn't go across the wire.
 * This roughly corresponds to {@link MessageContext} in the JAX-WS API.
 *
 * <p>
 * Usually a packet contains a {@link Message} in it, but sometimes
 * (such as for a reply of an one-way operation), a packet may
 * float around without a {@link Message} in it.
 *
 *
 * <a name="properties"></a>
 * <h2>Properties</h2>
 * <p>
 * Information frequently used inside the JAX-WS RI
 * is stored in the strongly-typed fields. Other information is stored
 * in terms of a generic {@link Map} (see {@link #otherProperties} and
 * {@link #invocationProperties}.)
 *
 * <p>
 * Some properties need to be retained between request and response,
 * some don't. For strongly typed fields, this characteristic is
 * statically known for each of them, and propagation happens accordingly.
 * For generic information stored in {@link Map}, {@link #otherProperties}
 * stores per-message scope information (which don't carry over to
 * response {@link Packet}), and {@link #invocationProperties}
 * stores per-invocation scope information (which carries over to
 * the response.)
 *
 * <p>
 * This object is used as the backing store of {@link MessageContext}, and
 * {@link LogicalMessageContext} and {@link SOAPMessageContext} will
 * be delegating to this object for storing/retrieving values.
 *
 *
 * <h3>Relationship to request/response context</h3>
 * <p>
 * {@link BindingProvider#getRequestContext() Request context} is used to
 * seed the initial values of {@link Packet}.
 * Some of those values go to strongly-typed fields, and others go to
 * {@link #invocationProperties}, as they need to be retained in the reply message.
 *
 * <p>
 * Similarly, {@link BindingProvider#getResponseContext() response context}
 * is constructed from {@link Packet} (or rather it's just a view of {@link Packet}.)
 * by using properties from both
 * {@link #invocationProperties} and {@link #otherProperties},
 * modulo properties named explicitly in {@link #getHandlerScopePropertyNames(boolean)}.
 * IOW, properties added to {@link #invocationProperties} and {@link #otherProperties}
 * are exposed to the response context by default.
 *
 *
 *
 * <h3>TODO</h3>
 * <ol>
 *  <li>this class needs to be cloneable since Message is copiable.
 *  <li>The three live views aren't implemented correctly. It will be
 *      more work to do so, although I'm sure it's possible.
 *  <li>{@link Property} annotation is to make it easy
 *      for {@link MessageContext} to export properties on this object,
 *      but it probably needs some clean up.
 * </ol>
 *
 * @author Kohsuke Kawaguchi
 */
public final class Packet extends DistributedPropertySet {

    /**
     * Creates a {@link Packet} that wraps a given {@link Message}.
     *
     * <p>
     * This method should be only used to create a fresh {@link Packet}.
     * To create a {@link Packet} for a reply, use {@link #createResponse(Message)}.
     *
     * @param request
     *      The request {@link Message}. Can be null.
     */
    public Packet(Message request) {
        this();
        this.message = request;
    }

    /**
     * Creates an empty {@link Packet} that doesn't have any {@link Message}.
     */
    public Packet() {
        this.invocationProperties = new HashMap<String,Object>();
    }

    /**
     * Used by {@link #createResponse(Message)}.
     */
    private Packet(Packet that) {
        that.copySatelliteInto(this);
        this.handlerConfig = that.handlerConfig;
        this.invocationProperties = that.invocationProperties;
        this.handlerScopePropertyNames = that.handlerScopePropertyNames;
        // copy other properties that need to be copied. is there any?
    }

    private Message message;

    /**
     * Gets the last {@link Message} set through {@link #setMessage(Message)}.
     *
     * @return
     *      may null. See the class javadoc for when it's null.
     */
    public Message getMessage() {
        return message;
    }

    /**
     * Sets a {@link Message} to this packet.
     *
     * @param message
     *      Can be null.
     */
    public void setMessage(Message message) {
        this.message = message;
    }

    /**
     * True if this message came from a transport (IOW inbound),
     * and in paricular from a "secure" transport. A transport
     * needs to set this flag appropriately.
     *
     * <p>
     * This is a requirement from the security team.
     */
    // TODO: expose this as a property
    public boolean wasTransportSecure;

    /**
     * This property holds the snapshot of HandlerConfiguration
     * at the time of invocation.
     * This property is used by MUPipe and HandlerPipe implementations.
     */
    @Property(BindingProviderProperties.JAXWS_HANDLER_CONFIG)
    public HandlerConfiguration handlerConfig;

    /**
     * If a message originates from a proxy stub that implements
     * a port interface, this field is set to point to that object.
     *
     * TODO: who's using this property? 
     */
    @Property(BindingProviderProperties.JAXWS_CLIENT_HANDLE_PROPERTY)
    public BindingProvider proxy;

    /**
     * The endpoint address to which this message is sent to.
     *
     * <p>
     * The JAX-WS spec allows this to be changed for each message,
     * so it's designed to be a property.
     *
     * <p>
     * Must not be null for a request message on the client. Otherwise
     * it's null.
     */
    public EndpointAddress endpointAddress;

    /**
     * @deprecated
     *      The programatic acccess should be done via
     *      {@link #endpointAddress}. This is for JAX-WS client applications
     *      that access this property via {@link BindingProvider#ENDPOINT_ADDRESS_PROPERTY}.
     */
    @Property(BindingProvider.ENDPOINT_ADDRESS_PROPERTY)
    public String getEndPointAddressString() {
        if(endpointAddress==null)
            return null;
        else
            return endpointAddress.toString();
    }

    public void setEndPointAddressString(String s) {
        if(s==null)
            this.endpointAddress = null;
        else
            this.endpointAddress = EndpointAddress.create(s);
    }

    /**
     * The value of {@link ContentNegotiation#PROPERTY} 
     * property.
     * 
     * This property is used only on the client side.
     */
    public ContentNegotiation contentNegotiation;

    @Property(ContentNegotiation.PROPERTY)
    public String getContentNegotiationString() {
        return (contentNegotiation != null) ? contentNegotiation.toString() : null;
    }

    public void setContentNegotiationString(String s) {
        if(s==null)
            contentNegotiation = null;
        else {
            try {
                contentNegotiation = ContentNegotiation.valueOf(s);
            } catch (IllegalArgumentException e) {
                // If the value is not recognized default to none
                contentNegotiation = ContentNegotiation.none;
            }
        }
    }

    /**
     * The list of MIME types that are acceptable to a receiver
     * of an outbound message.
     * 
     * This property is used only on the server side.
     * 
     * <p>The representation shall be that specified by the HTTP Accept 
     * request-header field.
     *
     * <p>The list of content types will be obtained from the transport
     * meta-data of a inbound message in a request/response message exchange. 
     * Hence this property will be set by the service-side transport pipe.
     *
     */
    public String acceptableMimeTypes;

    /**
     * When non-null, this object is consulted to
     * implement {@link WebServiceContext} methods
     * exposed to the user application.
     *
     * Used only on the server side.
     *
     * <p>
     * This property is set from the parameter
     * of {@link WSEndpoint.PipeHead#process}.
     */
    public WebServiceContextDelegate webServiceContextDelegate;

    /**
     * Used only on the server side so that the transport
     * can close the connection early.
     *
     * <p>
     * This field can be null. While a message is being processed,
     * this field can be set explicitly to null, to prevent
     * future pipes from closing a transport.
     *
     * <p>
     * This property is set from the parameter
     * of {@link WSEndpoint.PipeHead#process}.
     */
    public TransportBackChannel transportBackChannel;

    /**
     * The governing {@link WSEndpoint} in which this message is floating.
     *
     * <p>
     * This property is set if and only if this is on the server side.
     */
    public WSEndpoint endpoint;

    /**
     * The value of the SOAPAction header associated with the message.
     *
     * <p>
     * For outgoing messages, the transport may sends out this value.
     * If this field is null, the transport may choose to send <tt>""</tt>
     * (quoted empty string.)
     *
     * For incoming messages, the transport will set this field.
     * If the incoming message did not contain the SOAPAction header,
     * the transport sets this field to null.
     *
     * <p>
     * If the value is non-null, it must be always in the quoted form.
     * The value can be null.
     *
     * <p>
     * Note that the way the transport sends this value out depends on
     * transport and SOAP version.
     *
     * For HTTP transport and SOAP 1.1, BP requires that SOAPAction
     * header is present (See {@BP R2744} and {@BP R2745}.) For SOAP 1.2,
     * this is moved to the parameter of the "application/soap+xml".
     */
    @Property(BindingProviderProperties.SOAP_ACTION_PROPERTY)
    public String soapAction;

    /**
     * A hint indicating that whether a transport should expect
     * a reply back from the server.
     *
     * <p>
     * This property is used on the client-side for
     * outbound messages, so that a pipeline
     * can communicate to the terminal (or intermediate) {@link Pipe}s
     * about this knowledge.
     *
     * <p>
     * This property <b>MUST NOT</b> be used by 2-way transports
     * that have the transport back channel. Those transports
     * must always check a reply coming through the transport back
     * channel regardless of this value, and act accordingly.
     * (This is because the expectation of the client and
     * that of the server can be different, for example because
     * of a bug in user's configuration.)
     *
     * <p>
     * This property is for one-way transports, and more
     * specifically for the coordinator that correlates sent requests
     * and incoming replies, to decide whether to block
     * until a response is received.
     *
     * <p>
     * Also note that this property is related to
     * {@link WSDLOperation#isOneWay()} but not the same thing.
     * In fact in general, they are completely orthogonal.
     *
     * For example, the calling application can choose to invoke
     * {@link Dispatch#invoke(Object)} or {@link Dispatch#invokeOneWay(Object)}
     * with an operation (which determines the value of this property),
     * regardless of whether WSDL actually says it's one way or not.
     * So these two booleans can take any combinations.
     *
     *
     * <p>
     * When this property is {@link Boolean#TRUE}, it means that
     * the pipeline does not expect a reply from a server (and therefore
     * the correlator should not block for a reply message
     * -- if such a reply does arrive, it can be just ignored.)
     *
     * <p>
     * When this property is {@link Boolean#FALSE}, it means that
     * the pipeline expects a reply from a server (and therefore
     * the correlator should block to see if a reply message is received,
     *
     * <p>
     * This property is always set to {@link Boolean#TRUE} or
     * {@link Boolean#FALSE} when used on the request message
     * on the client side.
     * No other {@link Boolean} instances are allowed.
     * <p>
     *
     * In all other situations, this property is null.
     *
     */
    @Property(BindingProviderProperties.ONE_WAY_OPERATION)
    public Boolean expectReply;


    /**
     * This property will be removed in a near future.
     *
     * <p>
     * A part of what this flag represented moved to
     * {@link #expectReply} and the other part was moved
     * to {@link Message#isOneWay(WSDLPort)}. Please update
     * your code soon, or risk breaking your build!!
     */
    @Deprecated
    public Boolean isOneWay;

    /**
     * Lazily created set of handler-scope property names.
     *
     * <p>
     * We expect that this is only used when handlers are present
     * and they explicitly set some handler-scope values.
     *
     * @see #getHandlerScopePropertyNames(boolean)
     */
    private Set<String> handlerScopePropertyNames;

    /**
     * Bag to capture properties that are available for the whole
     * message invocation (namely on both requests and responses.)
     *
     * <p>
     * These properties are copied from a request to a response.
     * This is where we keep properties that are set by handlers.
     *
     * <p>
     * See <a href="#properties">class javadoc</a> for more discussion.
     *
     * @see #getHandlerScopePropertyNames(boolean)
     */
    public final Map<String,Object> invocationProperties;

    /**
     * Gets a {@link Set} that stores handler-scope properties.
     *
     * <p>
     * These properties will not be exposed to the response context.
     * Consequently, if a {@link Pipe} wishes to hide a property
     * to {@link ResponseContext}, it needs to add the property name
     * to this set.
     *
     * @param readOnly
     *      Return true if the caller only intends to read the value of this set.
     *      Internally, the {@link Set} is allocated lazily, and this flag helps
     *      optimizing the strategy.
     *
     * @return
     *      always non-null, possibly empty set that stores property names.
     */
    public final Set<String> getHandlerScopePropertyNames( boolean readOnly ) {
        Set<String> o = this.handlerScopePropertyNames;
        if(o==null) {
            if(readOnly)
                return Collections.emptySet();
            o = new HashSet<String>();
            this.handlerScopePropertyNames = o;
        }
        return o;
    }

    /**
     * This method no longer works.
     *
     * @deprecated
     *      Use {@link #getHandlerScopePropertyNames(boolean)}.
     *      To be removed once Tango components are updated.
     */
    public final Set<String> getApplicationScopePropertyNames( boolean readOnly ) {
        assert false;
        return new HashSet<String>();
    }

    /**
     * Creates a response {@link Packet} from a request packet ({@code this}).
     *
     * <p>
     * When a {@link Packet} for a reply is created, some properties need to be
     * copied over from a request to a response, and this method handles it correctly.
     *
     * @deprecated
     *      Use createClientResponse(Message) for client side and 
     *      createServerResponse(Message, String) for server side response
     *      creation.
     *
     * @param msg
     *      The {@link Message} that represents a reply. Can be null.
     */
    @Deprecated
    public Packet createResponse(Message msg) {
        Packet response = new Packet(this);
        response.setMessage(msg);
        return response;
    }

    /**
     * Creates a response {@link Packet} from a request packet ({@code this}).
     *
     * <p>
     * When a {@link Packet} for a reply is created, some properties need to be
     * copied over from a request to a response, and this method handles it correctly.
     *
     * @param msg
     *      The {@link Message} that represents a reply. Can be null.
     */
    public Packet createClientResponse(Message msg) {
        Packet response = new Packet(this);
        response.setMessage(msg);
        return response;
    }

    /**
     * Creates a server-side response {@link Packet} from a request
     * packet ({@code this}).
     *
     * @param msg The {@link Message} that represents a reply. Can be null.
     * @param binding The response Binding
     * @return response packet
     */
    public Packet createServerResponse(Message msg, WSDLPort wsdlPort, WSBinding binding) {
        Packet r = createClientResponse(msg);

        if (wsdlPort != null) {
            if (wsdlPort.getBinding() != null) {
                WSDLBoundOperation wbo = wsdlPort.getBinding().getOperation(message.getPayloadNamespaceURI(), message.getPayloadLocalPart());
                if (wbo.getOperation().isOneWay())
                    return r;
            }
        }

        // populate WS-Addressing headers
        return populateAddressingHeaders(binding, r, wsdlPort);
    }

    private Packet populateAddressingHeaders(WSBinding binding, Packet r, WSDLPort wsdlPort) {
        AddressingVersion ver = binding.getAddressingVersion();
        if (binding.getAddressingVersion() == null)
            return r;

        // if one-way, then dont populate any WS-A headers
        if (r.getMessage() == null)
            return r;

        WsaPipeHelper wsaHelper = ver.getWsaHelper(wsdlPort, binding);
        HeaderList hl = r.getMessage().getHeaders();

        // wsa:To
        WSEndpointReference replyTo = message.getHeaders().getReplyTo(ver, binding.getSOAPVersion());
        if (replyTo != null)
            hl.add(new StringHeader(ver.toTag, replyTo.getAddress()));

        // wsa:Action
        hl.add(new StringHeader(ver.actionTag, wsaHelper.getOutputAction(this)));

        // wsa:MessageID
        hl.add(new StringHeader(ver.messageIDTag, message.getID(binding)));

        // wsa:RelatesTo
        Header mid = message.getHeaders().get(ver.messageIDTag, true);
        if (mid != null)
            hl.add(new RelatesToHeader(ver.relatesToTag, mid.getStringContent()));

        WSEndpointReference refpEPR;
        if (r.getMessage().isFault()) {
            // choose FaultTo
            refpEPR = message.getHeaders().getFaultTo(ver);

            // if FaultTo is null, then use ReplyTo
            if (refpEPR == null)
                refpEPR = replyTo;
        } else {
            // choose ReplyTo
            refpEPR = replyTo;
        }
        if (refpEPR != null) {
            refpEPR.addReferenceParameters(hl);
        }

        return r;
    }

// completes TypedMap
    private static final PropertyMap model;

    static {
        model = parse(Packet.class);
    }

    protected PropertyMap getPropertyMap() {
        return model;
    }
}
