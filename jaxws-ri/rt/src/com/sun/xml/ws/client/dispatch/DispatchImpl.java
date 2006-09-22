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

package com.sun.xml.ws.client.dispatch;

import com.sun.xml.ws.addressing.EndpointReferenceUtil;
import com.sun.xml.ws.api.BindingID;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.WSBinding;
import com.sun.xml.ws.api.message.Message;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.model.wsdl.WSDLBoundPortType;
import com.sun.xml.ws.api.pipe.Pipe;
import com.sun.xml.ws.binding.BindingImpl;
import com.sun.xml.ws.client.*;
import com.sun.xml.ws.encoding.soap.DeserializationException;
import com.sun.xml.ws.fault.SOAPFaultBuilder;

import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.ws.*;
import javax.xml.ws.handler.MessageContext;
import javax.xml.ws.soap.SOAPFaultException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * TODO: update javadoc, use sandbox classes where can
 */

/**
 * The <code>DispatchImpl</code> abstract class provides support
 * for the dynamic invocation of a service endpoint operation using XML
 * constructs, JAXB objects or <code>SOAPMessage</code>. The <code>javax.xml.ws.Service</code>
 * interface acts as a factory for the creation of <code>DispatchImpl</code>
 * instances.
 *
 * @author WS Development Team
 * @version 1.0
 */
public abstract class DispatchImpl<T> extends Stub implements Dispatch<T> {

    final Service.Mode mode;
    final QName portname;
    Class<T> clazz;
    final WSServiceDelegate owner;
    final SOAPVersion soapVersion;
    static final long AWAIT_TERMINATION_TIME = 800L;

    /**
     *
     * @param port    dispatch instance is asssociated with this wsdl port qName
     * @param aClass  represents the class of the Message type associated with this Dispatch instance
     * @param mode    Service.mode associated with this Dispatch instance - Service.mode.MESSAGE or Service.mode.PAYLOAD
     * @param owner   Service that created the Dispatch
     * @param pipe    Master pipe for the pipeline
     * @param binding Binding of this Dispatch instance, current one of SOAP/HTTP or XML/HTTP
     */
    protected DispatchImpl(QName port, Class<T> aClass, Service.Mode mode, WSServiceDelegate owner, Pipe pipe, BindingImpl binding) {
        this(port, mode, owner, pipe, binding);
        this.clazz = aClass;
    }

    /**
     *
     * @param port    dispatch instance is asssociated with this wsdl port qName
     * @param mode    Service.mode associated with this Dispatch instance - Service.mode.MESSAGE or Service.mode.PAYLOAD
     * @param owner   Service that created the Dispatch
     * @param pipe    Master pipe for the pipeline
     * @param binding Binding of this Dispatch instance, current one of SOAP/HTTP or XML/HTTP
     */
    protected DispatchImpl(QName port, Service.Mode mode, WSServiceDelegate owner, Pipe pipe, BindingImpl binding) {
        // todo: fill the correct value for WSDLPort later
        super(pipe, binding, null, owner.getEndpointAddress(port));
        this.portname = port;
        this.mode = mode;
        this.owner = owner;
        this.soapVersion = binding.getSOAPVersion();
    }

    /**
     * Abstract method that is implemented by each concrete Dispatch class
     * @param msg  message passed in from the client program on the invocation
     * @return  The Message created returned as the Interface in actuallity a
     *          concrete Message Type
     */
    abstract Packet createPacket(T msg);

    /**
     * Obtains the value to return from the response message.
     */
    abstract T toReturnValue(Packet response);

    public final Response<T> invokeAsync(T param) {
        Invoker invoker = new Invoker(param);
        ResponseImpl<T> ft = new ResponseImpl<T>(invoker,null);
        invoker.setReceiver(ft);

        owner.getExecutor().execute(ft);
        return ft;
    }

    /* todo: Not sure that this meets the needs of tango for async callback */
    /* todo: Need to review with team                                       */

    public final Future<?> invokeAsync(T param, AsyncHandler<T> asyncHandler) {
        Invoker invoker = new Invoker(param);
        ResponseImpl<T> ft = new ResponseImpl<T>(invoker,asyncHandler);
        invoker.setReceiver(ft);

        // temp needed so that unit tests run and complete otherwise they may
        //not. Need a way to put this in the test harness or other way to do this
        //todo: as above
        ExecutorService exec = (ExecutorService) owner.getExecutor();
        try {
            exec.awaitTermination(AWAIT_TERMINATION_TIME, TimeUnit.MICROSECONDS);
        } catch (InterruptedException e) {
            throw new WebServiceException(e);
        }
        exec.execute(ft);
        return ft;
    }

    /**
     * Synchronously invokes a service.
     *
     * See {@link #process(Packet, RequestContext, ResponseContextReceiver)} on
     * why it takes a {@link RequestContext} and {@link ResponseContextReceiver} as a parameter.
     */
    public final T doInvoke(T in, RequestContext rc, ResponseContextReceiver receiver){
        Packet response = null;
        try {
            checkNullAllowed(in, rc, binding, mode);

            Packet message = createPacket(in);
            setProperties(message,true);
            response = process(message,rc,receiver);
            Message msg = response.getMessage();

            if(msg != null && msg.isFault()) {
                SOAPFaultBuilder faultBuilder = SOAPFaultBuilder.create(msg);
                // passing null means there is no checked excpetion we're looking for all
                // it will get back to us is a protocol exception
                throw (SOAPFaultException)faultBuilder.createException(null, msg);
            }
        } catch (JAXBException e) {
            //TODO: i18nify
            throw new DeserializationException("failed.to.read.response",e);
        } catch(RuntimeException e){
            //it could be a WebServiceException or a ProtocolException or any RuntimeException
            // resulting due to some internal bug.
            throw e;
        } catch(Throwable e){
            //its some other exception resulting from user error, wrap it in
            // WebServiceException
            throw new WebServiceException(e);
        }

        return toReturnValue(response);
    }

    public final T invoke(T in) {
        return doInvoke(in,requestContext,this);
    }

    public final void invokeOneWay(T in) {
        
        checkNullAllowed(in, requestContext, binding, mode);


        Packet request = createPacket(in);
        setProperties(request,false);
        Packet response = process(request,requestContext,this);
    }

    void setProperties(Packet packet, boolean expectReply) {
        packet.expectReply = expectReply;
    }

    static boolean isXMLHttp(WSBinding binding) {
        return  (binding.getBindingId().equals(BindingID.XML_HTTP)) ? true : false;
    }

    static boolean isPAYLOADMode(Service.Mode mode) {
           return  (mode == Service.Mode.PAYLOAD) ? true : false;
    }

    static void checkNullAllowed(Object in, RequestContext rc, WSBinding binding, Service.Mode mode) {

        if (in != null)
            return;

        //With HTTP Binding a null invocation parameter can not be used
        //with HTTP Request Method == POST
        if (isXMLHttp(binding)){
            if (methodNotOk(rc))
                throw new WebServiceException("A XML/HTTP request using MessageContext.HTTP_REQUEST_METHOD equals \"POST\" with a Null invocation Argument is not allowed");
        } else { //soapBinding
              if (mode == Service.Mode.MESSAGE )
                  throw new WebServiceException("SOAP/HTTP Binding in Service.Mode.message is not allowed with a null invocation argument");
        }
    }

    static boolean methodNotOk(RequestContext rc) {
        String requestMethod = (String)rc.get(MessageContext.HTTP_REQUEST_METHOD);
        String request = (requestMethod == null)? "POST": requestMethod;

        return  "POST".equalsIgnoreCase(request) ||
             "PUT".equalsIgnoreCase(request)? true: false;
    }

    public static void checkValidSOAPMessageDispatch(WSBinding binding, Service.Mode mode) {
        if (DispatchImpl.isXMLHttp(binding))
            throw new WebServiceException("Can not create Dispatch<SOAPMessage> with XML/HTTP Binding, SOAPBinding only.");
        if (DispatchImpl.isPAYLOADMode(mode))
            throw new WebServiceException("Can not create Dispatch<SOAPMessage> of Service.Mode.PAYLOAD, Service.Mode.MESSAGE only");
    }

    public static void checkValidDataSourceDispatch(WSBinding binding, Service.Mode mode) {
        if (!DispatchImpl.isXMLHttp(binding))
            throw new WebServiceException("Can not create Dispatch<DataSource> with SOAP Binding Binding, XML/HTTP Binding only.");
        if (DispatchImpl.isPAYLOADMode(mode))
            throw new WebServiceException("Can not create Dispatch<DataSource> of Service.Mode.PAYLOAD, Service.Mode.MESSAGE only");
    }

    /**
     * Calls {@link DispatchImpl#doInvoke(Object,RequestContext,ResponseContextReceiver)}.
     */
    private class Invoker implements Callable {
        private final T param;
        // snapshot the context now. this is necessary to avoid concurrency issue,
        // and is required by the spec
        private final RequestContext rc = requestContext.copy();

        /**
         * Because of the object instantiation order,
         * we can't take this as a constructor parameter.
         */
        private ResponseContextReceiver receiver;

        Invoker(T param) {
            this.param = param;
        }

        public T call() throws Exception {
            return doInvoke(param,rc,receiver);
        }

        void setReceiver(ResponseContextReceiver receiver) {
            this.receiver = receiver;
        }
    }

    public <T extends EndpointReference> T getEndpointReference(Class<T> clazz) {
        if (endpointReference != null) {
            return EndpointReferenceUtil.transform(clazz, endpointReference);
        }

        PortInfo port = owner.safeGetPort(portname);

        QName portTypeName = null;

        //Dispatch was created with WSDL. If created without WSDL then the portType name will be null.
        if(port.portModel != null){
            WSDLBoundPortType binding = port.portModel.getBinding();
            if(binding != null){
                portTypeName = binding.getPortTypeName();
            }
        }

        endpointReference = EndpointReferenceUtil.getEndpointReference(clazz,
                owner.getEndpointAddress(portname).toString(),
                owner.getServiceName(),
                portname.getLocalPart(),
                portTypeName, port.portModel != null);
        
        return (T) endpointReference;
    }
}
