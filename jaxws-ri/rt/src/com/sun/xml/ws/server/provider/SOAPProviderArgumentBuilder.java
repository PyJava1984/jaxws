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
package com.sun.xml.ws.server.provider;

import com.sun.istack.Nullable;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Messages;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.model.wsdl.WSDLPort;
import com.sun.xml.ws.fault.SOAPFaultBuilder;
import com.sun.xml.ws.resources.ServerMessages;

import javax.xml.soap.MimeHeader;
import javax.xml.soap.MimeHeaders;
import javax.xml.soap.SOAPException;
import javax.xml.soap.SOAPMessage;
import javax.xml.transform.Source;
import javax.xml.ws.Service;
import javax.xml.ws.WebServiceException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * @author Jitendra Kotamraju
 */
abstract class SOAPProviderArgumentBuilder<T> extends ProviderArgumentsBuilder<T> {
    protected final SOAPVersion soapVersion;

    private SOAPProviderArgumentBuilder(SOAPVersion soapVersion) {
        this.soapVersion = soapVersion;
    }

    static ProviderArgumentsBuilder create(ProviderEndpointModel model, SOAPVersion soapVersion) {
        if (model.mode == Service.Mode.PAYLOAD) {
            return new PayloadSource(soapVersion);
        } else {
            if(model.datatype==Source.class)
                return new MessageSource(soapVersion);
            if(model.datatype==SOAPMessage.class)
                return new SOAPMessageParameter(soapVersion);
            if(model.datatype==Message.class)
                return new MessageProviderArgumentBuilder(soapVersion);
            throw new WebServiceException(ServerMessages.PROVIDER_INVALID_PARAMETER_TYPE(model.implClass,model.datatype));
        }
    }

    private static final class PayloadSource extends SOAPProviderArgumentBuilder<Source> {
        PayloadSource(SOAPVersion soapVersion) {
            super(soapVersion);
        }

        protected Source getParameter(Packet packet) {
            return packet.getMessage().readPayloadAsSource();
        }

        protected Message getResponseMessage(Source source) {
            return Messages.createUsingPayload(source, soapVersion);
        }

        protected Message getResponseMessage(Exception e) {
            return SOAPFaultBuilder.createSOAPFaultMessage(soapVersion, null, e);
        }

    }

    private static final class MessageSource extends SOAPProviderArgumentBuilder<Source> {
        MessageSource(SOAPVersion soapVersion) {
            super(soapVersion);
        }

        protected Source getParameter(Packet packet) {
            return packet.getMessage().readEnvelopeAsSource();
        }

        protected Message getResponseMessage(Source source) {
            return Messages.create(source, soapVersion);
        }

        protected Message getResponseMessage(Exception e) {
            return SOAPFaultBuilder.createSOAPFaultMessage(soapVersion, null, e);
        }
    }

    private static final class SOAPMessageParameter extends SOAPProviderArgumentBuilder<SOAPMessage> {
        SOAPMessageParameter(SOAPVersion soapVersion) {
            super(soapVersion);
        }

        protected SOAPMessage getParameter(Packet packet) {
            try {
                return packet.getMessage().readAsSOAPMessage(packet, true);
            } catch (SOAPException se) {
                throw new WebServiceException(se);
            }
        }

        protected Message getResponseMessage(SOAPMessage soapMsg) {
            return Messages.create(soapMsg);
        }

        protected Message getResponseMessage(Exception e) {
            return SOAPFaultBuilder.createSOAPFaultMessage(soapVersion, null, e);
        }

        @Override
        protected Packet getResponse(Packet request, @Nullable SOAPMessage returnValue, WSDLPort port, WSBinding binding) {
            Packet response = super.getResponse(request, returnValue, port, binding);
            // Populate SOAPMessage's transport headers
            if (returnValue != null && response.supports(Packet.OUTBOUND_TRANSPORT_HEADERS)) {
                MimeHeaders hdrs = returnValue.getMimeHeaders();
                Map<String, List<String>> headers = new HashMap<String, List<String>>();
                Iterator i = hdrs.getAllHeaders();
                while(i.hasNext()) {
                    MimeHeader header = (MimeHeader)i.next();
                    if(header.getName().equalsIgnoreCase("SOAPAction"))
                        // SAAJ sets this header automatically, but it interferes with the correct operation of JAX-WS.
                        // so ignore this header.
                        continue;

                    List<String> list = headers.get(header.getName());
                    if (list == null) {
                        list = new ArrayList<String>();
                        headers.put(header.getName(), list);
                    }
                    list.add(header.getValue());
                }
                response.put(Packet.OUTBOUND_TRANSPORT_HEADERS, headers);
            }
            return response;
        }

    }

}
