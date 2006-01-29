/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 * 
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */
package com.sun.xml.ws.model;

import com.sun.xml.bind.api.TypeReference;
import com.sun.xml.bind.v2.model.nav.Navigator;
import com.sun.xml.ws.api.SOAPVersion;
import com.sun.xml.ws.api.model.ParameterBinding;
import com.sun.xml.ws.api.model.wsdl.WSDLBoundOperation;
import com.sun.xml.ws.api.model.wsdl.WSDLPart;
import com.sun.xml.ws.binding.soap.SOAPBindingImpl;
import com.sun.xml.ws.model.wsdl.WSDLPortImpl;
import com.sun.xml.ws.pept.presentation.MEP;

import javax.jws.Oneway;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebParam.Mode;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.jws.soap.SOAPBinding;
import javax.jws.soap.SOAPBinding.Style;
import javax.xml.namespace.QName;
import javax.xml.ws.AsyncHandler;
import javax.xml.ws.BindingType;
import javax.xml.ws.Holder;
import javax.xml.ws.RequestWrapper;
import javax.xml.ws.Response;
import javax.xml.ws.ResponseWrapper;
import javax.xml.ws.WebFault;
import javax.xml.ws.http.HTTPBinding;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.rmi.RemoteException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.Future;

/**
 * Creates a runtime model of a SEI (portClass).
 *
 * @author WS Developement Team
 */
public class RuntimeModeler {
    private String bindingId;
    private Class portClass;
    private AbstractSEIModelImpl runtimeModel;
    private com.sun.xml.ws.model.soap.SOAPBindingImpl defaultBinding;
    private String packageName;
    private String targetNamespace;
    private boolean isWrapped = true;
    private boolean usesWebMethod = false;
    private ClassLoader classLoader = null;
    private Object implementor;
    private final WSDLPortImpl binding;
    private QName serviceName;
    private QName portName;
    private Map<Class, Boolean> classUsesWebMethod = new HashMap<Class, Boolean>();

    /**
     *
     */
    public static final String PD_JAXWS_PACKAGE_PD  = ".jaxws.";
    /**
     *
     */
    public static final String JAXWS_PACKAGE_PD     = "jaxws.";
    public static final String RESPONSE             = "Response";
    public static final String RETURN               = "return";
    public static final String BEAN                 = "Bean";
    public static final String SERVICE              = "Service";
    public static final String PORT                 = "Port";
    public static final Class HOLDER_CLASS = Holder.class;
    public static final Class<RemoteException> REMOTE_EXCEPTION_CLASS = RemoteException.class;
    public static final Class RPC_LIT_PAYLOAD_CLASS = com.sun.xml.ws.encoding.jaxb.RpcLitPayload.class;

    /**
     * creates an instance of RunTimeModeler given a <code>portClass</code> and <code>bindingId</code>
     * @param portClass The SEI class to be modeled.
     * @param serviceName The ServiceName to use instead of one calculated from the implementation class
     * @param bindingId The binding identifier to be used when modeling the <code>portClass</code>.
     */
    public RuntimeModeler(Class portClass, QName serviceName, String bindingId) {
        this.portClass = portClass;
        this.serviceName = serviceName;
        this.binding = null;
        this.bindingId = bindingId;
    }

    /**
     *
     * creates an instance of RunTimeModeler given a <code>sei</code> and <code>binding</code>
     * @param sei The SEI class to be modeled.
     * @param serviceName The ServiceName to use instead of one calculated from the implementation class
     * @param binding The Binding representing WSDL Binding for the given port to be used when modeling the
     * <code>sei</code>.
     */
    public RuntimeModeler(Class sei, QName serviceName, WSDLPortImpl binding){
        this.portClass = sei;
        this.serviceName = serviceName;
        this.bindingId = binding.getBinding().getBindingId();
        this.binding = binding;
    }

    /**
     * creates an instance of RunTimeModeler given a <code>portClass</code> and <code>bindingId</code>
     * implementor object's class may not be portClass, as it could be proxy to
     * portClass webmethods.
     *
     * @param portClass The SEI class to be modeled.
     * @param implementor The object on which service methods are invoked
     * @param serviceName The ServiceName to use instead of one calculated from the implementation class
     * @param bindingId The binding identifier to be used when modeling the <code>portClass</code>.
     */
    public RuntimeModeler(Class portClass, Object implementor, QName serviceName, String bindingId) {
        this(portClass, serviceName, bindingId);
        this.implementor = implementor;
    }

    /**
     * creates an instance of RunTimeModeler given a <code>sei</code> class and <code>binding</code>
     * implementor object's class may not be sei Class, as it could be proxy to
     * sei webmethods.
     *
     * @param portClass The SEI class to be modeled.
     * @param implementor The object on which service methods are invoked
     * @param binding The Binding representing WSDL Binding for the given port to be used when modeling the
     * <code>sei</code>.
     */
    public RuntimeModeler(Class portClass, Object implementor, QName serviceName, WSDLPortImpl binding) {
        this(portClass, serviceName, binding);
        this.implementor = implementor;
    }


    /**
     * sets the classloader to be used when loading classes by the <code>RuntimeModeler</code>.
     * @param classLoader ClassLoader used to load classes
     */
    public void setClassLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /**
     * sets the PortName to be used by the <code>RuntimeModeler</code>.
     * @param portName The PortName to be used instead of the PortName
     * retrieved via annotations
     */
    public void setPortName(QName portName) {
        this.portName = portName;
    }

    private static <T extends Annotation> T getPrivClassAnnotation(final Class<?> clazz, final Class<T> T) {
        return AccessController.doPrivileged(new PrivilegedAction<T>() {
           public T run() {
               return clazz.getAnnotation(T);
           }
        });
    }

    private static <T extends Annotation> T getPrivMethodAnnotation(final Method method, final Class<T> T) {
        return AccessController.doPrivileged(new PrivilegedAction<T>() {
           public T run() {
               return method.getAnnotation(T);
           }
        });
    }

    private static Annotation[][] getPrivParameterAnnotations(final Method method) {
        return AccessController.doPrivileged(new PrivilegedAction<Annotation[][]>() {
           public Annotation[][] run() {
               return method.getParameterAnnotations();
           }
        });
    }

    //currently has many local vars which will be eliminated after debugging issues
    //first draft
    /**
     * builds the runtime model from the <code>portClass</code> using the binding ID <code>bindingId</code>.
     * @return the runtime model for the <code>portClass</code>.
     */
    public AbstractSEIModelImpl buildRuntimeModel() {
        runtimeModel = new SOAPSEIModel();
        Class clazz = portClass;
        WebService webService = getPrivClassAnnotation(portClass, WebService.class);
        if (webService == null) {
            throw new RuntimeModelerException("runtime.modeler.no.webservice.annotation",
                                       new Object[] {portClass.getCanonicalName()});
        }
        if (webService.endpointInterface().length() > 0) {
            clazz = getClass(webService.endpointInterface());
            WebService seiService = getPrivClassAnnotation(clazz, WebService.class);
            if (seiService == null) {
                throw new RuntimeModelerException("runtime.modeler.endpoint.interface.no.webservice",
                                    new Object[] {webService.endpointInterface()});
            }
        }
        if (serviceName == null)
            serviceName = getServiceName(portClass);
        runtimeModel.setServiceQName(serviceName);

        String portLocalName  = portClass.getSimpleName()+PORT;
        if (webService.portName().length() >0) {
            portLocalName = webService.portName();
        } else if (webService.name().length() >0) {
            portLocalName = webService.name()+PORT;
        }

        if (portName == null)
            portName = new QName(serviceName.getNamespaceURI(), portLocalName);
        if (!portName.getNamespaceURI().equals(serviceName.getNamespaceURI())) {
            throw new RuntimeModelerException("runtime.modeler.portname.servicename.namespace.mismatch",
                    new Object[] {serviceName, portName});
        }
        runtimeModel.setPortName(portName);

        processClass(clazz);
        if (runtimeModel.getJavaMethods().size() == 0)
            throw new RuntimeModelerException("runtime.modeler.no.operations",
                    portClass.getName());
        runtimeModel.postProcess();

        // TODO: this needs to be fixed properly --
        // when we are building RuntimeModel first before building WSDLModel,
        // we still need to do this correctyl
        if(binding!=null)
            runtimeModel.freeze(binding);

        return runtimeModel;
    }

    /**
     * utility method to load classes
     * @param className the name of the class to load
     * @return the class specified by <code>className</code>
     */
    protected Class getClass(String className) {
        try {
            if (classLoader == null)
                return Thread.currentThread().getContextClassLoader().loadClass(className);
            else
                return classLoader.loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new RuntimeModelerException("runtime.modeler.class.not.found",
                             new Object[] {className});
        }
    }

    protected void setUsesWebMethod(Class clazz, Boolean usesWebMethod) {
//        System.out.println("class: "+clazz.getName()+" uses WebMethod: "+usesWebMethod);
        classUsesWebMethod.put(clazz, usesWebMethod);
    }

    protected void determineWebMethodUse(Class clazz) {
        if (clazz == null)
            return;
        if (clazz.isInterface()) {
            setUsesWebMethod(clazz, false);
        }
        else {
            WebMethod webMethod;
            boolean hasWebMethod = false;
            for (Method method : clazz.getMethods()) {
                if (method.getDeclaringClass()!=clazz)
                    continue;
                webMethod = getPrivMethodAnnotation(method, WebMethod.class);
                if (webMethod != null &&
                    !webMethod.exclude()) {
                    hasWebMethod = true;
                    break;
                }
            }
            setUsesWebMethod(clazz, hasWebMethod);
        }
        determineWebMethodUse(clazz.getSuperclass());
    }

    void processClass(Class clazz) {
        determineWebMethodUse(clazz);
        WebService webService = getPrivClassAnnotation(clazz, WebService.class);
        String portTypeLocalName  = clazz.getSimpleName();
        if (webService.name().length() >0)
            portTypeLocalName = webService.name();


        targetNamespace = webService.targetNamespace();
        packageName = null;
        if (clazz.getPackage() != null)
            packageName = clazz.getPackage().getName();
        if (targetNamespace.length() == 0) {
            targetNamespace = getNamespace(packageName);
        }
        runtimeModel.setTargetNamespace(targetNamespace);
        QName portTypeName = new QName(targetNamespace, portTypeLocalName);
        runtimeModel.setPortTypeName(portTypeName);
        runtimeModel.setWSDLLocation(webService.wsdlLocation());

        javax.jws.soap.SOAPBinding soapBinding = getPrivClassAnnotation(clazz, javax.jws.soap.SOAPBinding.class);
        if (soapBinding != null) {
            isWrapped = soapBinding.parameterStyle().equals(
                javax.jws.soap.SOAPBinding.ParameterStyle.WRAPPED);
        }
        defaultBinding = createBinding(soapBinding);
        /*
         * if clazz != portClass then there is an SEI.  If there is an
         * SEI, then all methods should be processed.  However, if there is
         * no SEI, and the implementation class uses at least one
         * WebMethod annotation, then only methods with this annotation
         * will be processed.
         */
/*        if (clazz == portClass) {
            WebMethod webMethod;
            for (Method method : clazz.getMethods()) {
                webMethod = getPrivMethodAnnotation(method, WebMethod.class);
                if (webMethod != null &&
                    !webMethod.exclude()) {
                    usesWebMethod = true;
                    break;
                }
            }
        }*/

        for (Method method : clazz.getMethods()) {
            if (method.getDeclaringClass()==Object.class ||
                !isWebMethod(method, clazz)) {
                continue;
            }
            // TODO: binding can be null. We need to figure out how to post-process
            // RuntimeModel to link to WSDLModel
            processMethod(method, webService);
        }
    }

    protected boolean isWebMethod(Method method, Class clazz) {
        if (clazz.isInterface()) {
            return true;
        }
        Class declClass = method.getDeclaringClass();
        WebMethod webMethod = getPrivMethodAnnotation(method, WebMethod.class);
        if (webMethod != null && !webMethod.exclude()) {
            return true;
        }
        if (getPrivClassAnnotation(declClass, WebService.class) != null &&
            !classUsesWebMethod.get(declClass)) {
            return true;
        }
        return false;
    }

    /**
     * creates a runtime model <code>SOAPBinding</code> from a <code>javax.jws.soap.SOAPBinding</code> object
     * @param soapBinding the <code>javax.jws.soap.SOAPBinding</code> to model
     * @return returns the runtime model SOAPBinding corresponding to <code>soapBinding</code>
     */
    protected com.sun.xml.ws.model.soap.SOAPBindingImpl createBinding(javax.jws.soap.SOAPBinding soapBinding) {
        com.sun.xml.ws.model.soap.SOAPBindingImpl rtSOAPBinding =
            new com.sun.xml.ws.model.soap.SOAPBindingImpl();
        Style style = soapBinding!=null ? soapBinding.style() : Style.DOCUMENT;
        rtSOAPBinding.setStyle(style);
        //default soap version is 1.1, change it to soap 1.2 if the binding id says so
        if(SOAPVersion.SOAP_12.httpBindingId.equals(bindingId))
            rtSOAPBinding.setSOAPVersion(SOAPVersion.SOAP_12);
        return rtSOAPBinding;
    }

    /**
     * gets the namespace <code>String</code> for a given <code>packageName</code>
     * @param packageName the name of the package used to find a namespace
     * @return the namespace for the specified <code>packageName</code>
     */
    public static String getNamespace(String packageName) {
        if (packageName == null || packageName.length() == 0)
            return null;

        StringTokenizer tokenizer = new StringTokenizer(packageName, ".");
        String[] tokens;
        if (tokenizer.countTokens() == 0) {
            tokens = new String[0];
        } else {
            tokens = new String[tokenizer.countTokens()];
            for (int i=tokenizer.countTokens()-1; i >= 0; i--) {
                tokens[i] = tokenizer.nextToken();
            }
        }
        StringBuilder namespace = new StringBuilder("http://");
        for (int i=0; i<tokens.length; i++) {
            if (i!=0)
                namespace.append('.');
            namespace.append(tokens[i]);
        }
        namespace.append('/');
        return namespace.toString();
    }

    /**
     * creates the runtime model for a method on the <code>portClass</code>
     * @param method the method to model
     * @param webService the instance of the <code>WebService</code> annotation on the <code>portClass</code>
     */
    protected void processMethod(Method method, WebService webService) {
        if (!Modifier.isPublic(method.getModifiers())) {
            return;
        }

        WebMethod webMethod = getPrivMethodAnnotation(method, WebMethod.class);
        if (webMethod != null && webMethod.exclude())
            return;

        // If one WebMethod is used, then only methods with WebMethod will be
        // processed.
        if (usesWebMethod && webMethod == null) {
            return;
        }

        // Use implementor to find the exact invocation method as implementor
        // could be a proxy to portClass object
        JavaMethodImpl javaMethod;
        Class implementorClass = (implementor != null)
            ? implementor.getClass() : portClass;
        if (method.getDeclaringClass()==implementorClass) {
            javaMethod = new JavaMethodImpl(method);
        } else {
            try {
                Method tmpMethod = implementorClass.getMethod(method.getName(),
                    method.getParameterTypes());
                javaMethod = new JavaMethodImpl(tmpMethod);
            } catch (NoSuchMethodException e) {
                throw new RuntimeModelerException("runtime.modeler.method.not.found",
                    new Object[] {method.getName(), portClass.getName()});
            }
        }
        String methodName = method.getName();

        //set MEP -oneway, async, req/resp
        MEP mep = getMEP(method);
        javaMethod.setMEP(mep);

        String action = null;
        String operationName = method.getName();
        if (webMethod != null ) {
            action = webMethod.action();
            operationName = webMethod.operationName().length() > 0 ?
                webMethod.operationName() :
                operationName;
        }
        javaMethod.setOperationName(operationName);
        SOAPBinding methodBinding =
            method.getAnnotation(SOAPBinding.class);
        if (methodBinding == null && !method.getDeclaringClass().equals(portClass)) {
            if (!method.getDeclaringClass().isInterface()) {
                methodBinding = method.getDeclaringClass().getAnnotation(SOAPBinding.class);
            }
        }
        boolean methodIsWrapped = isWrapped;
        Style style = defaultBinding.getStyle();
        if (methodBinding != null) {
            com.sun.xml.ws.model.soap.SOAPBindingImpl mySOAPBinding = createBinding(methodBinding);
            style = mySOAPBinding.getStyle();
            if (action != null)
                mySOAPBinding.setSOAPAction(action);
            methodIsWrapped = methodBinding.parameterStyle().equals(
                javax.jws.soap.SOAPBinding.ParameterStyle.WRAPPED);
            javaMethod.setBinding(mySOAPBinding);
        } else {
            com.sun.xml.ws.model.soap.SOAPBindingImpl sb = new com.sun.xml.ws.model.soap.SOAPBindingImpl(defaultBinding);
            if (action != null)
                sb.setSOAPAction(action);
            else
                sb.setSOAPAction("");
            javaMethod.setBinding(sb);
        }
        if (!methodIsWrapped) {
            processDocBareMethod(javaMethod, methodName, webMethod, operationName,
                method, webService);
        } else if (style.equals(Style.DOCUMENT)) {
            processDocWrappedMethod(javaMethod, methodName, webMethod, operationName,
                method, webService);
        } else {
            processRpcMethod(javaMethod, methodName, webMethod, operationName,
                method, webService);
        }
        runtimeModel.addJavaMethod(javaMethod);
    }

    private MEP getMEP(Method m){
        if (m.isAnnotationPresent(Oneway.class)) {
            return MEP.ONE_WAY;
        }
        if(Response.class.isAssignableFrom(m.getReturnType())){
            return MEP.ASYNC_POLL;
        }else if(Future.class.isAssignableFrom(m.getReturnType())){
            return MEP.ASYNC_CALLBACK;
        }
        return MEP.REQUEST_RESPONSE;
    }

    /**
     * models a document/literal wrapped method
     * @param javaMethod the runtime model <code>JavaMethod</code> instance being created
     * @param methodName the runtime model <code>JavaMethod</code> instance being created
     * @param webMethod the runtime model <code>JavaMethod</code> instance being created
     * @param operationName the runtime model <code>JavaMethod</code> instance being created
     * @param method the <code>method</code> to model
     * @param webService The <code>WebService</code> annotation instance on the <code>portClass</code>
     */
    protected void processDocWrappedMethod(JavaMethodImpl javaMethod, String methodName,
                                           WebMethod webMethod, String operationName, Method method, WebService webService) {
        boolean isOneway = method.isAnnotationPresent(Oneway.class);
        RequestWrapper reqWrapper = method.getAnnotation(RequestWrapper.class);
        ResponseWrapper resWrapper = method.getAnnotation(ResponseWrapper.class);
        String beanPackage = packageName + PD_JAXWS_PACKAGE_PD;
        if (packageName.length() == 0)
            beanPackage = JAXWS_PACKAGE_PD;
        String requestClassName;
        if(reqWrapper != null && reqWrapper.className().length()>0){
            requestClassName = reqWrapper.className();
        }else{
            requestClassName = beanPackage + capitalize(method.getName());
        }


        String responseClassName;
        if(resWrapper != null && resWrapper.className().length()>0){
            responseClassName = resWrapper.className();
        }else{
            responseClassName = beanPackage + capitalize(method.getName()) + RESPONSE;
        }

        Class requestClass = getClass(requestClassName);

        String reqName = operationName;
        String reqNamespace = targetNamespace;
        if (reqWrapper != null) {
            if (reqWrapper.targetNamespace().length() > 0)
                reqNamespace = reqWrapper.targetNamespace();
            if (reqWrapper.localName().length() > 0)
                reqName = reqWrapper.localName();
        }
        QName reqElementName = new QName(reqNamespace, reqName);

        Class responseClass = null;
        String resName = operationName+"Response";
        String resNamespace = targetNamespace;
        if (!isOneway) {
            responseClass = getClass(responseClassName);
            if (resWrapper != null) {
                if (resWrapper.targetNamespace().length() > 0)
                    resNamespace = resWrapper.targetNamespace();
                if (resWrapper.localName().length() > 0)
                    resName = resWrapper.localName();
            }
        }
        QName resElementName = new QName(resNamespace, resName);

        TypeReference typeRef =
                new TypeReference(reqElementName, requestClass);
        WrapperParameter requestWrapper = new WrapperParameter(runtimeModel, typeRef,
            Mode.IN, 0);
        requestWrapper.setBinding(ParameterBinding.BODY);
        javaMethod.addParameter(requestWrapper);
        WrapperParameter responseWrapper = null;
        if (!isOneway) {
            typeRef = new TypeReference(resElementName, responseClass);
            responseWrapper = new WrapperParameter(runtimeModel, typeRef, Mode.OUT, -1);
            javaMethod.addParameter(responseWrapper);
            responseWrapper.setBinding(ParameterBinding.BODY);
        }

        // return value
        String resultName = RETURN;
        String resultTNS = "";
        QName resultQName = null;
        WebResult webResult = method.getAnnotation(WebResult.class);
        Class returnType = method.getReturnType();
        boolean isResultHeader = false;
        if (webResult != null) {
            if (webResult.name().length() > 0)
                resultName = webResult.name();
            resultTNS = webResult.targetNamespace();
            isResultHeader = webResult.header();
            if (resultTNS.length() == 0 && webResult.header()) {
                // headers must have a namespace
                resultTNS = targetNamespace;
            }
            resultQName = new QName(resultTNS, resultName);
        } else if (!isOneway && !returnType.getName().equals("void") && !javaMethod.isAsync()) {
            if(resultQName == null){
                resultQName = new QName(resultTNS, RETURN);
            }
        }

        if(javaMethod.isAsync()){
            returnType = getAsyncReturnType(method, returnType);
            resultQName = new QName(RETURN);
        }

        if (!isOneway && (returnType != null) && (!returnType.getName().equals("void"))) {
            Annotation[] rann = method.getAnnotations();
            if (resultQName.getLocalPart() != null) {
                TypeReference rTypeReference = new TypeReference(resultQName, returnType, rann);
                ParameterImpl returnParameter = new ParameterImpl(runtimeModel, rTypeReference, Mode.OUT, -1);
                if (isResultHeader) {
                    returnParameter.setBinding(ParameterBinding.HEADER);
                    javaMethod.addParameter(returnParameter);
                } else {
                    returnParameter.setBinding(ParameterBinding.BODY);
                    responseWrapper.addWrapperChild(returnParameter);
                }
            }
        }

        //get WebParam
        Class<?>[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Annotation[][] pannotations = getPrivParameterAnnotations(method);
        int pos = 0;
        for (Class clazzType : parameterTypes) {
            String partName=null;
            String paramName = "arg"+pos;
            String paramNamespace = "";
            boolean isHeader = false;

            if(javaMethod.isAsync() && AsyncHandler.class.isAssignableFrom(clazzType)){
                continue;
            }

            boolean isHolder = HOLDER_CLASS.isAssignableFrom(clazzType);
            //set the actual type argument of Holder in the TypeReference
            if (isHolder) {
                if(clazzType==Holder.class){
                    clazzType = Navigator.REFLECTION.erasure(((ParameterizedType)genericParameterTypes[pos]).getActualTypeArguments()[0]);
                }
            }
            Mode paramMode = isHolder ? Mode.INOUT : Mode.IN;
            for (Annotation annotation : pannotations[pos]) {
                if (annotation.annotationType() == WebParam.class) {
                    WebParam webParam = (WebParam) annotation;
                    if (webParam.name().length() > 0)
                        paramName = webParam.name();
                    isHeader = webParam.header();
                    if(webParam.partName().length() > 0)
                        partName = webParam.partName();
                    else
                        partName = paramName;
                    if (isHeader) // headers cannot be in empty namespace
                        paramNamespace = targetNamespace;
                    if (!webParam.targetNamespace().equals("")) {
                        paramNamespace = webParam.targetNamespace();
                    }
                    paramMode = webParam.mode();
                    if (isHolder && paramMode == Mode.IN)
                        paramMode = Mode.INOUT;
                    break;
                }
            }
            QName paramQName = new QName(paramNamespace, paramName);
            typeRef =
                new TypeReference(paramQName, clazzType, pannotations[pos]);
            ParameterImpl param = new ParameterImpl(runtimeModel, typeRef, paramMode, pos++);

            if (isHeader) {
                param.setBinding(ParameterBinding.HEADER);
                javaMethod.addParameter(param);
                param.setPartName(partName);
            } else {
                param.setBinding(ParameterBinding.BODY);
                if (paramMode!=Mode.OUT) {
                    requestWrapper.addWrapperChild(param);
                }
                if (paramMode!=Mode.IN) {
                    if (isOneway) {
                        throw new RuntimeModelerException("runtime.modeler.oneway.operation.no.out.parameters",
                                new Object[] {portClass.getCanonicalName(), methodName});
                    }
                    responseWrapper.addWrapperChild(param);
                }
            }
        }
        processExceptions(javaMethod, method);
    }


    /**
     * models a rpc/literal method
     * @param javaMethod the runtime model <code>JavaMethod</code> instance being created
     * @param methodName the name of the <code>method</code> being modeled.
     * @param webMethod the <code>WebMethod</code> annotations instance on the <code>method</code>
     * @param operationName the WSDL operation name for this <code>method</code>
     * @param method the runtime model <code>JavaMethod</code> instance being created
     * @param webService the runtime model <code>JavaMethod</code> instance being created
     */
    protected void processRpcMethod(JavaMethodImpl javaMethod, String methodName,
                                    WebMethod webMethod, String operationName, Method method, WebService webService) {
        boolean isOneway = method.isAnnotationPresent(Oneway.class);
        QName reqElementName = new QName(targetNamespace, operationName);
        QName resElementName = null;

        //build ordered list
        Map<Integer, ParameterImpl> resRpcParams = new HashMap<Integer, ParameterImpl>();
        Map<Integer, ParameterImpl> reqRpcParams = new HashMap<Integer, ParameterImpl>();
        if(binding != null && binding.getBinding().isRpcLit()){
            binding.getBinding().finalizeRpcLitBinding();
        }

        if (!isOneway) {
            resElementName = new QName(targetNamespace, operationName+RESPONSE);
        }

        TypeReference typeRef = new TypeReference(reqElementName, RPC_LIT_PAYLOAD_CLASS);
        WrapperParameter requestWrapper = new WrapperParameter(runtimeModel, typeRef, Mode.IN, 0);
        requestWrapper.setInBinding(ParameterBinding.BODY);
        javaMethod.addParameter(requestWrapper);
        WrapperParameter responseWrapper = null;
        if (!isOneway) {
            typeRef = new TypeReference(resElementName, RPC_LIT_PAYLOAD_CLASS);
            responseWrapper = new WrapperParameter(runtimeModel, typeRef, Mode.OUT, -1);
            responseWrapper.setOutBinding(ParameterBinding.BODY);
            javaMethod.addParameter(responseWrapper);
        }

        Class returnType = method.getReturnType();
        String resultName = RETURN;
        String resultTNS = targetNamespace;
        String resultPartName = resultName;
        boolean isResultHeader = false;
        WebResult webResult = method.getAnnotation(WebResult.class);

        if (webResult != null) {
            isResultHeader = webResult.header();
            if (webResult.name().length() > 0)
                resultName = webResult.name();
            if (webResult.partName().length() > 0) {
                resultPartName = webResult.partName();
                if (!isResultHeader)
                    resultName = resultPartName;
            } else
                resultPartName = resultName;
            if (webResult.targetNamespace().length() > 0)
                resultTNS = webResult.targetNamespace();
            isResultHeader = webResult.header();
        }
        QName resultQName;
        if (isResultHeader)
            resultQName = new QName(resultTNS, resultName);
        else
            resultQName = new QName(resultName);

        if(javaMethod.isAsync()){
            returnType = getAsyncReturnType(method, returnType);
        }

        if (!isOneway && returnType!=null && returnType!=void.class) {
            Annotation[] rann = method.getAnnotations();
            TypeReference rTypeReference = new TypeReference(resultQName, returnType, rann);
            ParameterImpl returnParameter = new ParameterImpl(runtimeModel, rTypeReference, Mode.OUT, -1);
            returnParameter.setPartName(resultPartName);
            if(isResultHeader){
                returnParameter.setBinding(ParameterBinding.HEADER);
                javaMethod.addParameter(returnParameter);
            }else{
                ParameterBinding rb = getBinding(operationName, resultPartName, false, Mode.OUT);
                returnParameter.setBinding(rb);
                if(rb.isBody() || rb.isUnbound()){
                    WSDLPart p = getPart(new QName(targetNamespace,operationName), resultPartName, Mode.OUT);
                    if(p == null)
                        resRpcParams.put(resRpcParams.size(), returnParameter);
                    else
                        resRpcParams.put(p.getIndex(), returnParameter);
                    //responseWrapper.addWrapperChild(returnParameter);
                }else{
                    javaMethod.addParameter(returnParameter);
                }
            }
        }

        //get WebParam
        Class<?>[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Annotation[][] pannotations = getPrivParameterAnnotations(method);
        int pos = 0;
        for (Class clazzType : parameterTypes) {
            String paramName = "";
            String paramNamespace = "";
            String partName = "";
            boolean isHeader = false;

            if(javaMethod.isAsync() && AsyncHandler.class.isAssignableFrom(clazzType)){
                continue;
            }

            boolean isHolder = HOLDER_CLASS.isAssignableFrom(clazzType);
            //set the actual type argument of Holder in the TypeReference
            if (isHolder) {
                if (clazzType==Holder.class)
                    clazzType = Navigator.REFLECTION.erasure(((ParameterizedType)genericParameterTypes[pos]).getActualTypeArguments()[0]);
            }
            Mode paramMode = isHolder ? Mode.INOUT : Mode.IN;
            for (Annotation annotation : pannotations[pos]) {
                if (annotation.annotationType() == javax.jws.WebParam.class) {
                    javax.jws.WebParam webParam = (javax.jws.WebParam) annotation;
                    paramName = webParam.name();
                    partName = webParam.partName();
                    isHeader = webParam.header();
                    WebParam.Mode mode = webParam.mode();
                    paramNamespace = webParam.targetNamespace();
                    if (isHolder && mode == Mode.IN)
                        mode = Mode.INOUT;
                    paramMode = mode;
                    break;
                }
            }

            if (paramName.length() == 0) {
                paramName = "arg"+pos;
            }
            if (partName.length() == 0) {
                partName = paramName;
            } else if (!isHeader) {
                paramName = partName;
            }
            if (partName.length() == 0) {
                partName = paramName;
            }

            QName paramQName;
            if (!isHeader) {
                //its rpclit body param, set namespace to ""
                paramQName = new QName("", paramName);
            } else {
                if (paramNamespace.length() == 0)
                    paramNamespace = targetNamespace;
                paramQName = new QName(paramNamespace, paramName);
            }
            typeRef =
                new TypeReference(paramQName, clazzType, pannotations[pos]);

            ParameterImpl param = new ParameterImpl(runtimeModel, typeRef, paramMode, pos++);
            param.setPartName(partName);

            if(paramMode == Mode.INOUT){
                ParameterBinding pb = getBinding(operationName, partName, isHeader, Mode.IN);
                param.setInBinding(pb);
                pb = getBinding(operationName, partName, isHeader, Mode.OUT);
                param.setOutBinding(pb);
            }else{
                if (isHeader) {
                    param.setBinding(ParameterBinding.HEADER);
                } else {
                    ParameterBinding pb = getBinding(operationName, partName, false, paramMode);
                    param.setBinding(pb);
                }
            }
            if(param.getInBinding().isBody()){
                if(!param.isOUT()){
                    WSDLPart p = getPart(new QName(targetNamespace,operationName), partName, Mode.IN);
                    if(p == null)
                        reqRpcParams.put(reqRpcParams.size(), param);
                    else
                        reqRpcParams.put(p.getIndex(), param);
                    //requestWrapper.addWrapperChild(param);
                }

                if(!param.isIN()){
                    if (isOneway) {
                            throw new RuntimeModelerException("runtime.modeler.oneway.operation.no.out.parameters",
                                    new Object[] {portClass.getCanonicalName(), methodName});
                    }
                    WSDLPart p = getPart(new QName(targetNamespace,operationName), partName, Mode.OUT);
                    if(p == null)
                        resRpcParams.put(resRpcParams.size(), param);
                    else
                        resRpcParams.put(p.getIndex(), param);
//                        responseWrapper.addWrapperChild(param);
                }
            }else{
                javaMethod.addParameter(param);
            }
        }
        for(int i = 0; i < reqRpcParams.size();i++)
            requestWrapper.addWrapperChild(reqRpcParams.get(i));
        for(int i = 0; i < resRpcParams.size();i++)
            responseWrapper.addWrapperChild(resRpcParams.get(i));
        processExceptions(javaMethod, method);
    }

    /**
     * models the exceptions thrown by <code>method</code> and adds them to the <code>javaMethod</code>
     * runtime model object
     * @param javaMethod the runtime model object to add the exception model objects to
     * @param method the <code>method</code> from which to find the exceptions to model
     */
    protected void processExceptions(JavaMethodImpl javaMethod, Method method) {
        for (Class<?> exception : method.getExceptionTypes()) {
            if (REMOTE_EXCEPTION_CLASS.isAssignableFrom(exception))
                continue;
            Class exceptionBean;
            Annotation[] anns;
            Method faultInfoMethod = getWSDLExceptionFaultInfo(exception);
            ExceptionType exceptionType = ExceptionType.WSDLException;
            String namespace = targetNamespace;
            String name = exception.getSimpleName();
            if (faultInfoMethod == null)  {
                String beanPackage = packageName + PD_JAXWS_PACKAGE_PD;
                String className = beanPackage+ name + BEAN;
                exceptionBean = getClass(className);
                exceptionType = ExceptionType.UserDefined;
                anns = exceptionBean.getAnnotations();
            } else {
                WebFault webFault = (WebFault)exception.getAnnotation(WebFault.class);
                exceptionBean = faultInfoMethod.getReturnType();
                anns = faultInfoMethod.getAnnotations();
                if (webFault.targetNamespace().length() > 0)
                    namespace = webFault.targetNamespace();
                name = webFault.name();
            }
            QName faultName = new QName(namespace, name);
            TypeReference typeRef = new TypeReference(faultName, exceptionBean, anns);
            CheckedExceptionImpl checkedException =
                new CheckedExceptionImpl(exception, typeRef, exceptionType);
            javaMethod.addException(checkedException);
        }
    }

    /**
     * returns the method that corresponds to "getFaultInfo".  Returns null if this is not an
     * exception generated from a WSDL
     * @param exception the class to search for the "getFaultInfo" method
     * @return the method named "getFaultInfo" if this is an exception generated from WSDL or an
     * exception that contains the <code>WebFault</code> annotation.  Otherwise it returns null
     */
    protected Method getWSDLExceptionFaultInfo(Class exception) {
        if (!exception.isAnnotationPresent(WebFault.class))
            return null;
        try {
            return exception.getMethod("getFaultInfo", new Class[0]);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    /**
     * models a document/literal bare method
     * @param javaMethod the runtime model <code>JavaMethod</code> instance being created
     * @param methodName the runtime model <code>JavaMethod</code> instance being created
     * @param webMethod the runtime model <code>JavaMethod</code> instance being created
     * @param operationName the runtime model <code>JavaMethod</code> instance being created
     * @param method the runtime model <code>JavaMethod</code> instance being created
     * @param webService the runtime model <code>JavaMethod</code> instance being created
     */
    protected void processDocBareMethod(JavaMethodImpl javaMethod, String methodName,
                                        WebMethod webMethod, String operationName, Method method, WebService webService) {

        String resultName = operationName+RESPONSE;
        String resultTNS = targetNamespace;
        String resultPartName = null;
        boolean isResultHeader = false;
        WebResult webResult = method.getAnnotation(WebResult.class);
        if (webResult != null) {
            if (webResult.name().length() > 0)
                resultName = webResult.name();
            if (webResult.targetNamespace().length() > 0)
                resultTNS = webResult.targetNamespace();
            resultPartName = webResult.partName();
            isResultHeader = webResult.header();
        }

        Class returnType = method.getReturnType();

        if(javaMethod.isAsync()){
            returnType = getAsyncReturnType(method, returnType);
        }

        if ((returnType != null) && (!returnType.getName().equals("void"))) {
            Annotation[] rann = method.getAnnotations();
            if (resultName != null) {
                QName responseQName = new QName(resultTNS, resultName);
                TypeReference rTypeReference = new TypeReference(responseQName, returnType, rann);
                ParameterImpl returnParameter = new ParameterImpl(runtimeModel, rTypeReference, Mode.OUT, -1);

                if(resultPartName == null || (resultPartName.length() == 0)){
                    resultPartName = resultName;
                }
                returnParameter.setPartName(resultPartName);
                if(isResultHeader){
                    returnParameter.setBinding(ParameterBinding.HEADER);
                }else{
                    ParameterBinding rb = getBinding(operationName, resultPartName, false, Mode.OUT);
                    returnParameter.setBinding(rb);
                }
                javaMethod.addParameter(returnParameter);
            }
        }

        //get WebParam
        Class<?>[] parameterTypes = method.getParameterTypes();
        Type[] genericParameterTypes = method.getGenericParameterTypes();
        Annotation[][] pannotations = getPrivParameterAnnotations(method);
        int pos = 0;
        for (Class clazzType : parameterTypes) {
            String paramName = operationName; //method.getName();
            String partName = null;
            String requestNamespace = targetNamespace;
            boolean isHeader = false;

            //async
            if(javaMethod.isAsync() && AsyncHandler.class.isAssignableFrom(clazzType)){
                continue;
            }

            boolean isHolder = HOLDER_CLASS.isAssignableFrom(clazzType);
            //set the actual type argument of Holder in the TypeReference
            if (isHolder) {
                if (clazzType==Holder.class)
                    clazzType = Navigator.REFLECTION.erasure(((ParameterizedType)genericParameterTypes[pos]).getActualTypeArguments()[0]);
            }

            Mode paramMode = isHolder ? Mode.INOUT : Mode.IN;
            for (Annotation annotation : pannotations[pos]) {
                if (annotation.annotationType() == javax.jws.WebParam.class) {
                    javax.jws.WebParam webParam = (javax.jws.WebParam) annotation;
                    if (webParam.name().length() > 0)
                        paramName = webParam.name();
                    partName = webParam.partName();
                    if (!webParam.targetNamespace().equals("")) {
                        requestNamespace = webParam.targetNamespace();
                    }
                    isHeader = webParam.header();
                    paramMode = webParam.mode();
                    if (isHolder && paramMode == Mode.IN)
                        paramMode = Mode.INOUT;
                    break;
                }
            }

            QName requestQName = new QName(requestNamespace, paramName);
            //doclit/wrapped
            TypeReference typeRef = //operationName with upper 1 char
                new TypeReference(requestQName, clazzType,
                    pannotations[pos]);

            ParameterImpl param = new ParameterImpl(runtimeModel, typeRef, paramMode, pos++);
            if(partName == null || (partName.length() == 0)){
                partName = paramName;
            }
            param.setPartName(partName);
            if(paramMode == Mode.INOUT){
                ParameterBinding pb = getBinding(operationName, partName, isHeader, Mode.IN);
                param.setInBinding(pb);
                pb = getBinding(operationName, partName, isHeader, Mode.OUT);
                param.setOutBinding(pb);
            }else{
                if (isHeader){
                    param.setBinding(ParameterBinding.HEADER);
                }else{
                    ParameterBinding pb = getBinding(operationName, partName, false, paramMode);
                    param.setBinding(pb);
                }
            }
            javaMethod.addParameter(param);
        }
        processExceptions(javaMethod, method);
    }

    private Class getAsyncReturnType(Method method, Class returnType) {
        if(Response.class.isAssignableFrom(returnType)){
            Type ret = method.getGenericReturnType();
            return Navigator.REFLECTION.erasure(((ParameterizedType)ret).getActualTypeArguments()[0]);
        }else{
            Type[] types = method.getGenericParameterTypes();
            Class[] params = method.getParameterTypes();
            int i = 0;
            for(Class cls : params){
                if(AsyncHandler.class.isAssignableFrom(cls)){
                    return Navigator.REFLECTION.erasure(((ParameterizedType)types[i]).getActualTypeArguments()[0]);
                }
                i++;
            }
        }
        return returnType;
    }

    /**
     * utility to capitalize the first letter in a string
     * @param name the string to capitalize
     * @return the capitalized string
     */
    public static String capitalize(String name) {
        if (name == null || name.length() == 0) {
            return name;
        }
        char chars[] = name.toCharArray();
        chars[0] = Character.toUpperCase(chars[0]);
        return new String(chars);
    }

    /*
    * Return service QName
    */
    /**
     * gets the <code>wsdl:serviceName</code> for a given implementation class
     * @param implClass the implementation class
     * @return the <code>wsdl:serviceName</code> for the <code>implClass</code>
     */
    public static QName getServiceName(Class<?> implClass) {
        if (implClass.isInterface()) {
            throw new RuntimeModelerException("runtime.modeler.cannot.get.serviceName.from.interface",
                                    implClass.getCanonicalName());
        }

        String name = implClass.getSimpleName()+SERVICE;
        String packageName = null;
        if (implClass.getPackage() != null)
            packageName = implClass.getPackage().getName();

        WebService webService = implClass.getAnnotation(WebService.class);
        if (webService == null) {
            throw new RuntimeModelerException("runtime.modeler.no.webservice.annotation",
                                    new Object[] {implClass.getCanonicalName()});
        }
        if (webService.serviceName().length() > 0) {
            name = webService.serviceName();
        }
        String targetNamespace = getNamespace(packageName);
        if (webService.targetNamespace().length() > 0) {
            targetNamespace = webService.targetNamespace();
        } else if (targetNamespace == null) {
            throw new RuntimeModelerException("runtime.modeler.no.package",
                             new Object[] {implClass.getName()});
        }



        return new QName(targetNamespace, name);
    }

    /**
     * gets the <code>wsdl:portName</code> for a given implementation class
     * @param implClass the implementation class
     * @param targetNamespace Namespace URI for service name
     * @return the <code>wsdl:portName</code> for the <code>implClass</code>
     */
    public static QName getPortName(Class<?> implClass, String targetNamespace) {
        WebService webService = implClass.getAnnotation(WebService.class);
        if (webService == null) {
            throw new RuntimeModelerException("runtime.modeler.no.webservice.annotation",
                new Object[] {implClass.getCanonicalName()});
        }
        String name;
        if (webService.portName().length() > 0) {
            name = webService.portName();
        } else if (webService.name().length() > 0) {
            name = webService.name()+PORT;
        } else {
            name = implClass.getSimpleName()+PORT;
        }

        if (targetNamespace == null) {
            if (webService.targetNamespace().length() > 0) {
                targetNamespace = webService.targetNamespace();
            } else {
                String packageName = null;
                if (implClass.getPackage() != null) {
                    packageName = implClass.getPackage().getName();
                }
                targetNamespace = getNamespace(packageName);
                if (targetNamespace == null) {
                    throw new RuntimeModelerException("runtime.modeler.no.package",
                        new Object[] {implClass.getName()});
                }
            }

        }

        return new QName(targetNamespace, name);
    }

    /**
     * Gives portType QName from implementatorClass or SEI
     * @param  implOrSeiClass cant be null
     * @return  <code>wsdl:portType@name</code>, null if it could not find the annotated class.
     */
    public static QName getPortTypeName(Class<?> implOrSeiClass){
        assert(implOrSeiClass != null);
        Class<?> clazz = implOrSeiClass;
        if (!implOrSeiClass.isAnnotationPresent(WebService.class))
                throw new RuntimeModelerException("runtime.modeler.no.webservice.annotation",
                                           implOrSeiClass.getCanonicalName());

        if (!implOrSeiClass.isInterface()) {
            WebService webService = implOrSeiClass.getAnnotation(WebService.class);
            String epi = webService.endpointInterface();
            if (epi.length() > 0) {
                try {
                    clazz = Thread.currentThread().getContextClassLoader().loadClass(epi);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeModelerException("runtime.modeler.class.not.found", epi);
                }
                if (!clazz.isAnnotationPresent(javax.jws.WebService.class)) {
                    throw new RuntimeModelerException("runtime.modeler.endpoint.interface.no.webservice",
                                        webService.endpointInterface());
                }
            }
        }

        WebService webService = clazz.getAnnotation(WebService.class);
        String name = webService.name();
        if(name.length() == 0){
            name = clazz.getSimpleName();
        }

        String tns = webService.targetNamespace();
        if (tns.length() == 0)
            tns = getNamespace(clazz.getPackage().getName());
        if (tns == null) {
            throw new RuntimeModelerException("runtime.modeler.no.package",
                new Object[] {clazz.getName()});
        }
        return new QName(tns, name);
    }

    public static String getBindingId(Class<?> implClass) {
        BindingType bindingType = implClass.getAnnotation(BindingType.class);
        if (bindingType != null) {
            String bindingId = bindingType.value();
            if (bindingId.length() > 0) {
                if (!bindingId.equals(javax.xml.ws.soap.SOAPBinding.SOAP11HTTP_BINDING)
                    && !bindingId.equals(javax.xml.ws.soap.SOAPBinding.SOAP12HTTP_BINDING)
                    && !bindingId.equals(HTTPBinding.HTTP_BINDING)
                    && !bindingId.equals(SOAPBindingImpl.X_SOAP12HTTP_BINDING)) {
                    throw new IllegalArgumentException("Wrong binding id "+bindingId+" in @BindingType");
                }
                return bindingId;
            }
        }
        return null;
    }

    private ParameterBinding getBinding(String operation, String part, boolean isHeader, Mode mode){
        if(binding == null){
            if(isHeader)
                return ParameterBinding.HEADER;
            else
                return ParameterBinding.BODY;
        }
        QName opName = new QName(binding.getBinding().getPortType().getName().getNamespaceURI(), operation);
        return binding.getBinding().getBinding(opName, part, mode);
    }

    private WSDLPart getPart(QName opName, String partName, Mode mode){
        if(binding != null){
            WSDLBoundOperation bo = binding.getBinding().get(opName);
            if(bo != null)
                return bo.getPart(partName, mode);
        }
        return null;
    }


    public String getBindingId() {
        return bindingId;
    }

}
