
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

package com.sun.tools.ws.processor.modeler.wsdl;

import com.sun.tools.ws.api.wsdl.TWSDLExtensible;
import com.sun.tools.ws.api.wsdl.TWSDLExtension;
import com.sun.tools.ws.processor.config.WSDLModelInfo;
import com.sun.tools.ws.processor.generator.Names;
import com.sun.tools.ws.processor.model.Fault;
import com.sun.tools.ws.processor.model.Model;
import com.sun.tools.ws.processor.model.ModelObject;
import com.sun.tools.ws.processor.model.Operation;
import com.sun.tools.ws.processor.model.Parameter;
import com.sun.tools.ws.processor.model.Port;
import com.sun.tools.ws.processor.model.java.JavaException;
import com.sun.tools.ws.processor.modeler.JavaSimpleTypeCreator;
import com.sun.tools.ws.processor.modeler.Modeler;
import com.sun.tools.ws.processor.modeler.ModelerException;
import com.sun.tools.ws.processor.util.ProcessorEnvironment;
import com.sun.tools.ws.wsdl.document.BindingFault;
import com.sun.tools.ws.wsdl.document.BindingOperation;
import com.sun.tools.ws.wsdl.document.Documentation;
import com.sun.tools.ws.wsdl.document.Kinds;
import com.sun.tools.ws.wsdl.document.Message;
import com.sun.tools.ws.wsdl.document.MessagePart;
import com.sun.tools.ws.wsdl.document.OperationStyle;
import com.sun.tools.ws.wsdl.document.WSDLDocument;
import com.sun.tools.ws.wsdl.document.jaxws.JAXWSBinding;
import com.sun.tools.ws.wsdl.document.mime.MIMEContent;
import com.sun.tools.ws.wsdl.document.mime.MIMEMultipartRelated;
import com.sun.tools.ws.wsdl.document.mime.MIMEPart;
import com.sun.tools.ws.wsdl.document.schema.SchemaKinds;
import com.sun.tools.ws.wsdl.document.soap.SOAPBinding;
import com.sun.tools.ws.wsdl.document.soap.SOAPBody;
import com.sun.tools.ws.wsdl.document.soap.SOAPFault;
import com.sun.tools.ws.wsdl.document.soap.SOAPHeader;
import com.sun.tools.ws.wsdl.document.soap.SOAPOperation;
import com.sun.tools.ws.wsdl.framework.GloballyKnown;
import com.sun.tools.ws.wsdl.framework.NoSuchEntityException;
import com.sun.tools.ws.wsdl.parser.Constants;
import com.sun.tools.ws.wsdl.parser.Util;
import com.sun.tools.ws.wsdl.parser.WSDLParser;
import com.sun.xml.ws.util.localization.Localizable;
import com.sun.xml.ws.util.localization.LocalizableMessageFactory;
import com.sun.xml.ws.util.xml.XmlUtil;
import org.w3c.dom.Element;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;

/**
 *
 * @author WS Development Team
 *
 * Base class for WSDL->Model classes.
 */
public abstract class WSDLModelerBase implements Modeler {
    public WSDLModelerBase(WSDLModelInfo modelInfo, Properties options) {
        //init();
        _modelInfo = modelInfo;
        _options = options;
        _messageFactory =
            new LocalizableMessageFactory("com.sun.tools.ws.resources.modeler");
        _conflictingClassNames = null;
        _env = modelInfo.getParent().getEnvironment();
        hSet = null;
        reqResNames = new HashSet<String>();
    }


    protected WSDLParser createWSDLParser(){
        return new WSDLParser(_modelInfo);
    }

    /**
     * Builds model from WSDL document. Model contains abstraction which is used by the
     * generators to generate the stub/tie/serializers etc. code.
     *
     * @see Modeler#buildModel()
     */
    public Model buildModel() {
        return null;
    }

    protected WSDLModelInfo getWSDLModelInfo(){
        return _modelInfo;
    }

    protected Documentation getDocumentationFor(Element e) {
        String s = XmlUtil.getTextForNode(e);
        if (s == null) {
            return null;
        } else {
            return new Documentation(s);
        }
    }

    protected void checkNotWsdlElement(Element e) {
        // possible extensibility element -- must live outside the WSDL namespace
        if (e.getNamespaceURI().equals(Constants.NS_WSDL))
            Util.fail("parsing.invalidWsdlElement", e.getTagName());
    }

    /**
     * @param port
     * @param wsdlPort
     */
    protected void applyPortMethodCustomization(Port port, com.sun.tools.ws.wsdl.document.Port wsdlPort) {
        if(isProvider(wsdlPort))
            return;
        JAXWSBinding jaxwsBinding = (JAXWSBinding)getExtensionOfType(wsdlPort, JAXWSBinding.class);

        String portMethodName = (jaxwsBinding != null)?((jaxwsBinding.getMethodName() != null)?jaxwsBinding.getMethodName().getName():null):null;
        if(portMethodName != null){
            port.setPortGetter(portMethodName);
        }else{
            portMethodName = Names.getPortName(port);
            portMethodName = getEnvironment().getNames().validJavaClassName(portMethodName);
            port.setPortGetter("get"+portMethodName);
        }

    }

    protected boolean isProvider(com.sun.tools.ws.wsdl.document.Port wsdlPort){
        JAXWSBinding portCustomization = (JAXWSBinding)getExtensionOfType(wsdlPort, JAXWSBinding.class);
        Boolean isProvider = (portCustomization != null)?portCustomization.isProvider():null;
        if(isProvider != null){
            return isProvider;
        }

        JAXWSBinding jaxwsGlobalCustomization = (JAXWSBinding)getExtensionOfType(document.getDefinitions(), JAXWSBinding.class);
        isProvider = (jaxwsGlobalCustomization != null)?jaxwsGlobalCustomization.isProvider():null;
        if(isProvider != null)
            return isProvider;
        return false;
    }

//    protected void createParentFault(Fault fault) {
//        AbstractType faultType = fault.getBlock().getType();
//        AbstractType parentType = null;
//
//
//        if (parentType == null) {
//            return;
//        }
//
//        if (fault.getParentFault() != null) {
//            return;
//        }
//        Fault parentFault =
//            new Fault(((AbstractType)parentType).getName().getLocalPart());
//        /* this is what it really should be but for interop with JAXRPC 1.0.1 we are not doing
//         * this at this time.
//         *
//         * TODO - we should double-check this; the above statement might not be true anymore.
//         */
//        QName faultQName =
//            new QName(
//                fault.getBlock().getName().getNamespaceURI(),
//                parentFault.getName());
//        Block block = new Block(faultQName);
//        block.setType((AbstractType)parentType);
//        parentFault.setBlock(block);
//        parentFault.addSubfault(fault);
//        createParentFault(parentFault);
//    }

//    protected void createSubfaults(Fault fault) {
//        AbstractType faultType = fault.getBlock().getType();
//        Iterator subtypes = null;
//        if (subtypes != null) {
//            AbstractType subtype;
//            while (subtypes.hasNext()) {
//                subtype = (AbstractType)subtypes.next();
//                Fault subFault = new Fault(subtype.getName().getLocalPart());
//                /* this is what it really is but for interop with JAXRPC 1.0.1 we are not doing
//                 * this at this time
//                 *
//                 * TODO - we should double-check this; the above statement might not be true anymore.
//                 */
//                QName faultQName =
//                    new QName(
//                        fault.getBlock().getName().getNamespaceURI(),
//                        subFault.getName());
//                Block block = new Block(faultQName);
//                block.setType(subtype);
//                subFault.setBlock(block);
//                fault.addSubfault(subFault);
//                createSubfaults(subFault);
//            }
//        }
//    }

    protected SOAPBody getSOAPRequestBody() {
        SOAPBody requestBody =
            (SOAPBody)getAnyExtensionOfType(info.bindingOperation.getInput(),
                SOAPBody.class);
        if (requestBody == null) {
            // the WSDL document is invalid
            throw new ModelerException(
                "wsdlmodeler.invalid.bindingOperation.inputMissingSoapBody",
                info.bindingOperation.getName());
        }
        return requestBody;
    }

    protected boolean isRequestMimeMultipart() {
        for (TWSDLExtension extension: info.bindingOperation.getInput().extensions()) {
            if (extension.getClass().equals(MIMEMultipartRelated.class)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isResponseMimeMultipart() {
        for (TWSDLExtension extension: info.bindingOperation.getOutput().extensions()) {
            if (extension.getClass().equals(MIMEMultipartRelated.class)) {
                return true;
            }
        }
        return false;
    }




    protected SOAPBody getSOAPResponseBody() {
        SOAPBody responseBody =
            (SOAPBody)getAnyExtensionOfType(info.bindingOperation.getOutput(),
                SOAPBody.class);
        if (responseBody == null) {
            // the WSDL document is invalid
            throw new ModelerException(
                "wsdlmodeler.invalid.bindingOperation.outputMissingSoapBody",
                info.bindingOperation.getName());
        }
        return responseBody;
    }

    protected com.sun.tools.ws.wsdl.document.Message getOutputMessage() {
        if (info.portTypeOperation.getOutput() == null)
            return null;
        return info.portTypeOperation.getOutput().resolveMessage(info.document);
    }

    protected com.sun.tools.ws.wsdl.document.Message getInputMessage() {
        return info.portTypeOperation.getInput().resolveMessage(info.document);
    }

    /**
     * @param body request or response body, represents soap:body
     * @param message Input or output message, equivalent to wsdl:message
     * @return iterator over MessagePart
     */
    protected List<MessagePart> getMessageParts(
        SOAPBody body,
        com.sun.tools.ws.wsdl.document.Message message, boolean isInput) {
        String bodyParts = body.getParts();
        ArrayList<MessagePart> partsList = new ArrayList<MessagePart>();
        List<MessagePart> parts = new ArrayList<MessagePart>();

        //get Mime parts
        List mimeParts;
        if(isInput)
            mimeParts = getMimeContentParts(message, info.bindingOperation.getInput());
        else
            mimeParts = getMimeContentParts(message, info.bindingOperation.getOutput());

        if (bodyParts != null) {
            StringTokenizer in = new StringTokenizer(bodyParts.trim(), " ");
            while (in.hasMoreTokens()) {
                String part = in.nextToken();
                MessagePart mPart = message.getPart(part);
                if (null == mPart) {
                    throw new ModelerException(
                        "wsdlmodeler.error.partsNotFound",
                        part, message.getName());
                }
                mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_BODY_BINDING);
                partsList.add(mPart);
            }
        } else {
            for(Iterator iter = message.parts();iter.hasNext();) {
                MessagePart mPart = (MessagePart)iter.next();
                if(!mimeParts.contains(mPart))
                    mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_BODY_BINDING);
                partsList.add(mPart);
            }
        }

        for(Iterator iter = message.parts();iter.hasNext();) {
            MessagePart mPart = (MessagePart)iter.next();
            if(mimeParts.contains(mPart)) {
                mPart.setBindingExtensibilityElementKind(MessagePart.WSDL_MIME_BINDING);
                parts.add(mPart);
            }else if(partsList.contains(mPart)) {
                mPart.setBindingExtensibilityElementKind(MessagePart.SOAP_BODY_BINDING);
                parts.add(mPart);
            }
        }

        return parts;
    }

    /**
     * @param message
     * @return MessageParts referenced by the mime:content
     */
    protected List<MessagePart> getMimeContentParts(Message message, TWSDLExtensible ext) {
        ArrayList<MessagePart> mimeContentParts = new ArrayList<MessagePart>();

        for (MIMEPart mimePart : getMimeParts(ext)) {
            MessagePart part = getMimeContentPart(message, mimePart);
            if (part != null)
                mimeContentParts.add(part);
        }
        return mimeContentParts;
    }

    /**
     * @param mimeParts
     */
    protected boolean validateMimeParts(Iterable<MIMEPart> mimeParts) {
        boolean gotRootPart = false;
        List<MIMEContent> mimeContents = new ArrayList<MIMEContent>();
        for (MIMEPart mPart : mimeParts) {
            for (TWSDLExtension obj : mPart.extensions()) {
                if (obj instanceof SOAPBody) {
                    if (gotRootPart) {
                        //bug fix: 5024020
                        warn("mimemodeler.invalidMimePart.moreThanOneSOAPBody",
                            new Object[]{info.operation.getName().getLocalPart()});
                        return false;
                    }
                    gotRootPart = true;
                } else if (obj instanceof MIMEContent) {
                    mimeContents.add((MIMEContent) obj);
                }
            }
            if(!validateMimeContentPartNames(mimeContents.iterator()))
                return false;
            if(mPart.getName() != null) {
                //bug fix: 5024018
                warn("mimemodeler.invalidMimePart.nameNotAllowed",
                        info.portTypeOperation.getName());
            }
        }
        return true;

    }

    private MessagePart getMimeContentPart(Message message, MIMEPart part) {
        for( MIMEContent mimeContent : getMimeContents(part) ) {
            String mimeContentPartName = mimeContent.getPart();
            MessagePart mPart = message.getPart(mimeContentPartName);
            //RXXXX mime:content MUST have part attribute
            if(null == mPart) {
                throw new ModelerException("wsdlmodeler.error.partsNotFound",
                        mimeContentPartName, message.getName());
            }
            mPart.setBindingExtensibilityElementKind(MessagePart.WSDL_MIME_BINDING);
            return mPart;
        }
        return null;
    }

    //List of mimeTypes
    protected List<String> getAlternateMimeTypes(List<MIMEContent> mimeContents) {
        List<String> mimeTypes = new ArrayList<String>();
        //validateMimeContentPartNames(mimeContents.iterator());
//        String mimeType = null;
        for(MIMEContent mimeContent:mimeContents){
            String mimeType = getMimeContentType(mimeContent);
            if(!mimeTypes.contains(mimeType))
                mimeTypes.add(mimeType);
        }
        return mimeTypes;
    }

    private boolean validateMimeContentPartNames(Iterator mimeContents) {
        //validate mime:content(s) in the mime:part as per R2909
        while(mimeContents.hasNext()){
            String mimeContnetPart = null;
            if(mimeContnetPart == null) {
                mimeContnetPart = getMimeContentPartName((MIMEContent)mimeContents.next());
                if(mimeContnetPart == null) {
                    warn("mimemodeler.invalidMimeContent.missingPartAttribute",
                            new Object[] {info.operation.getName().getLocalPart()});
                    return false;
                }
            }else {
                String newMimeContnetPart = getMimeContentPartName((MIMEContent)mimeContents.next());
                if(newMimeContnetPart == null) {
                    warn("mimemodeler.invalidMimeContent.missingPartAttribute",
                            new Object[] {info.operation.getName().getLocalPart()});
                    return false;
                }else if(!newMimeContnetPart.equals(mimeContnetPart)) {
                    //throw new ModelerException("mimemodeler.invalidMimeContent.differentPart");
                    warn("mimemodeler.invalidMimeContent.differentPart");
                    return false;
                }
            }
        }
        return true;
    }

    protected Iterable<MIMEPart> getMimeParts(TWSDLExtensible ext) {
        MIMEMultipartRelated multiPartRelated =
            (MIMEMultipartRelated) getAnyExtensionOfType(ext,
                    MIMEMultipartRelated.class);
        if(multiPartRelated == null) {
            return Collections.emptyList();
        }
        return multiPartRelated.getParts();
    }

    //returns MIMEContents
    protected List<MIMEContent> getMimeContents(MIMEPart part) {
        List<MIMEContent> mimeContents = new ArrayList<MIMEContent>();
        for (TWSDLExtension mimeContent : part.extensions()) {
            if (mimeContent instanceof MIMEContent) {
                mimeContents.add((MIMEContent) mimeContent);
            }
        }
        //validateMimeContentPartNames(mimeContents.iterator());
        return mimeContents;
    }

    private String getMimeContentPartName(MIMEContent mimeContent){
        /*String partName = mimeContent.getPart();
        if(partName == null){
            throw new ModelerException("mimemodeler.invalidMimeContent.missingPartAttribute",
                    new Object[] {info.operation.getName().getLocalPart()});
        }
        return partName;*/
        return mimeContent.getPart();
    }

    private String getMimeContentType(MIMEContent mimeContent){
        String mimeType = mimeContent.getType();
        if(mimeType == null){
            throw new ModelerException("mimemodeler.invalidMimeContent.missingTypeAttribute",
                    info.operation.getName().getLocalPart());
        }
        return mimeType;
    }

    /**
     * For Document/Lit the wsdl:part should only have element attribute and
     * for RPC/Lit or RPC/Encoded the wsdl:part should only have type attribute
     * inside wsdl:message.
     */
    protected boolean isStyleAndPartMatch(
        SOAPOperation soapOperation,
        MessagePart part) {

        // style attribute on soap:operation takes precedence over the
        // style attribute on soap:binding

        if ((soapOperation != null) && (soapOperation.getStyle() != null)) {
            if ((soapOperation.isDocument()
                && (part.getDescriptorKind() != SchemaKinds.XSD_ELEMENT))
                || (soapOperation.isRPC()
                    && (part.getDescriptorKind() != SchemaKinds.XSD_TYPE))) {
                return false;
            }
        } else {
            if ((info.soapBinding.isDocument()
                && (part.getDescriptorKind() != SchemaKinds.XSD_ELEMENT))
                || (info.soapBinding.isRPC()
                    && (part.getDescriptorKind() != SchemaKinds.XSD_TYPE))) {
                return false;
            }
        }

        return true;
    }



    protected String getRequestNamespaceURI(SOAPBody body) {
        String namespaceURI = body.getNamespace();
        if (namespaceURI == null) {
            // the WSDL document is invalid
            // at least, that's my interpretation of section 3.5 of the WSDL 1.1 spec!
            throw new ModelerException(
                "wsdlmodeler.invalid.bindingOperation.inputSoapBody.missingNamespace",
                info.bindingOperation.getName());
        }
        return namespaceURI;
    }

    protected String getResponseNamespaceURI(SOAPBody body) {
        String namespaceURI = body.getNamespace();
        if (namespaceURI == null) {
            // the WSDL document is invalid
            // at least, that's my interpretation of section 3.5 of the WSDL 1.1 spec!
            throw new ModelerException(
                "wsdlmodeler.invalid.bindingOperation.outputSoapBody.missingNamespace",
                info.bindingOperation.getName());
        }
        return namespaceURI;
    }

    /**
     * @return List of SOAPHeader extensions
     */
    protected List<SOAPHeader> getHeaderExtensions(TWSDLExtensible extensible) {
        List<SOAPHeader> headerList = new ArrayList<SOAPHeader>();
        for (TWSDLExtension extension : extensible.extensions()) {
            if (extension.getClass()==MIMEMultipartRelated.class) {
                for( MIMEPart part : ((MIMEMultipartRelated) extension).getParts() ) {
                    boolean isRootPart = isRootPart(part);
                    for (TWSDLExtension obj : part.extensions()) {
                        if (obj instanceof SOAPHeader) {
                            //bug fix: 5024015
                            if (!isRootPart) {
                                warn(
                                    "mimemodeler.warning.IgnoringinvalidHeaderPart.notDeclaredInRootPart",
                                    new Object[]{
                                        info.bindingOperation.getName()});
                                return new ArrayList<SOAPHeader>();
                            }
                            headerList.add((SOAPHeader) obj);
                        }
                    }
                }
            } else if (extension instanceof SOAPHeader) {
                headerList.add((SOAPHeader) extension);
            }
        }
         return headerList;
    }

    /**
     * @param part
     * @return true if part is the Root part
     */
    private boolean isRootPart(MIMEPart part) {
        for (TWSDLExtension twsdlExtension : part.extensions()) {
            if (twsdlExtension instanceof SOAPBody)
                return true;
        }
        return false;
    }

    protected Set getDuplicateFaultNames() {
        // look for fault messages with the same soap:fault name
        Set<QName> faultNames = new HashSet<QName>();
        Set<QName> duplicateNames = new HashSet<QName>();
        for (Iterator iter = info.bindingOperation.faults(); iter.hasNext();) {
            BindingFault bindingFault = (BindingFault)iter.next();
            com.sun.tools.ws.wsdl.document.Fault portTypeFault = null;
            for (Iterator iter2 = info.portTypeOperation.faults();
                 iter2.hasNext();
                ) {
                com.sun.tools.ws.wsdl.document.Fault aFault =
                    (com.sun.tools.ws.wsdl.document.Fault)iter2.next();

                if (aFault.getName().equals(bindingFault.getName())) {
                    if (portTypeFault != null) {
                        // the WSDL document is invalid
                        throw new ModelerException(
                            "wsdlmodeler.invalid.bindingFault.notUnique",
                            bindingFault.getName(),
                            info.bindingOperation.getName());
                    } else {
                        portTypeFault = aFault;
                    }
                }
            }
            if (portTypeFault == null) {
                // the WSDL document is invalid
                throw new ModelerException(
                    "wsdlmodeler.invalid.bindingFault.notFound",
                    bindingFault.getName(),
                    info.bindingOperation.getName());

            }
            SOAPFault soapFault =
                (SOAPFault)getExtensionOfType(bindingFault, SOAPFault.class);
            if (soapFault == null) {
                // the WSDL document is invalid
                throw new ModelerException(
                    "wsdlmodeler.invalid.bindingFault.outputMissingSoapFault",
                    bindingFault.getName(),
                    info.bindingOperation.getName());
            }

            com.sun.tools.ws.wsdl.document.Message faultMessage =
                portTypeFault.resolveMessage(info.document);
            Iterator iter2 = faultMessage.parts();
            if (!iter2.hasNext()) {
                // the WSDL document is invalid
                throw new ModelerException(
                    "wsdlmodeler.invalid.bindingFault.emptyMessage",
                    bindingFault.getName(),
                    faultMessage.getName());
            }
            //  bug fix: 4852729
            if (useWSIBasicProfile && (soapFault.getNamespace() != null)) {
                warn(
                    "wsdlmodeler.warning.r2716r2726",
                    new Object[] { "soapbind:fault", soapFault.getName()});
            }
            String faultNamespaceURI = soapFault.getNamespace();
            if (faultNamespaceURI == null) {
                faultNamespaceURI =
                    portTypeFault.getMessage().getNamespaceURI();
            }
            String faultName = faultMessage.getName();
            QName faultQName = new QName(faultNamespaceURI, faultName);
            if (faultNames.contains(faultQName)) {
                duplicateNames.add(faultQName);
            } else {
                faultNames.add(faultQName);
            }
        }
        return duplicateNames;
    }


    /**
     * @param operation
     * @return true if operation has valid body parts
     */
    protected boolean validateBodyParts(BindingOperation operation) {
        boolean isRequestResponse =
            info.portTypeOperation.getStyle()
            == OperationStyle.REQUEST_RESPONSE;
        List<MessagePart> inputParts = getMessageParts(getSOAPRequestBody(), getInputMessage(), true);
        if(!validateStyleAndPart(operation, inputParts))
            return false;

        if(isRequestResponse){
            List<MessagePart> outputParts = getMessageParts(getSOAPResponseBody(), getOutputMessage(), false);
            if(!validateStyleAndPart(operation, outputParts))
                return false;
        }
        return true;
    }

    /**
     * @param operation
     * @return true if operation has valid style and part
     */
    private boolean validateStyleAndPart(BindingOperation operation, List<MessagePart> parts) {
        SOAPOperation soapOperation =
            (SOAPOperation) getExtensionOfType(operation, SOAPOperation.class);
        for (MessagePart part : parts) {
            if (part.getBindingExtensibilityElementKind() == MessagePart.SOAP_BODY_BINDING) {
                if (!isStyleAndPartMatch(soapOperation, part))
                    return false;
            }
        }
        return true;
    }

    protected String getLiteralJavaMemberName(Fault fault) {
        String javaMemberName;

        QName memberName = fault.getElementName();
        javaMemberName = fault.getJavaMemberName();
        if (javaMemberName == null)
            javaMemberName = memberName.getLocalPart();
        return javaMemberName;
    }

    /**
     * @param ext
     * @param message
     * @param name
     * @return List of MimeContents from ext
     */
    protected List<MIMEContent> getMimeContents(TWSDLExtensible ext, Message message, String name) {
        for (MIMEPart mimePart : getMimeParts(ext)) {
            List<MIMEContent> mimeContents = getMimeContents(mimePart);
            for (MIMEContent mimeContent : mimeContents) {
                if (mimeContent.getPart().equals(name))
                    return mimeContents;
            }
        }
        return null;
    }

    protected ProcessorEnvironment getEnvironment() {
        return _env;
    }

    protected void warn(Localizable msg) {
        getEnvironment().warn(msg);
    }

    protected void warn(String key) {
        getEnvironment().warn(_messageFactory.getMessage(key));
    }

    protected void warn(String key, String arg) {
        getEnvironment().warn(_messageFactory.getMessage(key, arg));
    }

    protected void error(String key, String arg) {
        getEnvironment().error(_messageFactory.getMessage(key, arg));
    }

    protected void warn(String key, Object[] args) {
        getEnvironment().warn(_messageFactory.getMessage(key, args));
    }

    protected void info(String key) {
        getEnvironment().info(_messageFactory.getMessage(key));
    }

    protected void info(String key, String arg) {
        getEnvironment().info(_messageFactory.getMessage(key, arg));
    }

    protected String makePackageQualified(String s, QName name) {
        return makePackageQualified(s, name, true);
    }

    protected String makePackageQualified(
        String s,
        QName name,
        boolean useNamespaceMapping) {
        String javaPackageName = null;
        if (useNamespaceMapping) {
            javaPackageName = getJavaPackageName(name);
        }
        if (javaPackageName != null) {
            return javaPackageName + "." + s;
        } else if (
            _modelInfo.getJavaPackageName() != null
                && !_modelInfo.getJavaPackageName().equals("")) {
            return _modelInfo.getJavaPackageName() + "." + s;
        } else {
            return s;
        }
    }

    protected QName makePackageQualified(QName name) {
        return makePackageQualified(name, true);
    }

    protected QName makePackageQualified(
        QName name,
        boolean useNamespaceMapping) {
        return new QName(
            name.getNamespaceURI(),
            makePackageQualified(name.getLocalPart(), name));
    }

    protected String makeNameUniqueInSet(String candidateName, Set names) {
        String baseName = candidateName;
        String name = baseName;
        for (int i = 2; names.contains(name); ++i) {
            name = baseName + Integer.toString(i);
        }
        return name;
    }

    protected String getUniqueName(
        com.sun.tools.ws.wsdl.document.Operation operation,
        boolean hasOverloadedOperations) {
        if (hasOverloadedOperations) {
            return operation.getUniqueKey().replace(' ', '_');
        } else {
            return operation.getName();
        }
    }

    protected String getUniqueParameterName(
        Operation operation,
        String baseName) {
        Set<String> names = new HashSet<String>();
        for (Iterator iter = operation.getRequest().getParameters();
             iter.hasNext();
            ) {
            Parameter p = (Parameter)iter.next();
            names.add(p.getName());
        }
        for (Iterator iter = operation.getResponse().getParameters();
             iter.hasNext();
            ) {
            Parameter p = (Parameter)iter.next();
            names.add(p.getName());
        }
        String candidateName = baseName;
        while (names.contains(candidateName)) {
            candidateName += "_prime";
        }
        return candidateName;
    }

    protected String getNonQualifiedNameFor(QName name) {
        return _env.getNames().validJavaClassName(name.getLocalPart());
    }

    protected static void setDocumentationIfPresent(
        ModelObject obj,
        Documentation documentation) {
        if (documentation != null && documentation.getContent() != null) {
            obj.setProperty(WSDL_DOCUMENTATION, documentation.getContent());
        }
    }

    protected static QName getQNameOf(GloballyKnown entity) {
        return new QName(
            entity.getDefining().getTargetNamespaceURI(),
            entity.getName());
    }

    protected static TWSDLExtension getExtensionOfType(
            TWSDLExtensible extensible,
            Class type) {
        for (TWSDLExtension extension:extensible.extensions()) {
            if (extension.getClass().equals(type)) {
                return extension;
            }
        }

        return null;
    }

    protected TWSDLExtension getAnyExtensionOfType(
        TWSDLExtensible extensible,
        Class type) {
        if(extensible == null)
            return null;
        for (TWSDLExtension extension:extensible.extensions()) {
            if(extension.getClass().equals(type)) {
                return extension;
            }else if (extension.getClass().equals(MIMEMultipartRelated.class) &&
                    (type.equals(SOAPBody.class) || type.equals(MIMEContent.class)
                            || type.equals(MIMEPart.class))) {
                for (MIMEPart part : ((MIMEMultipartRelated)extension).getParts()) {
                    //bug fix: 5024001
                    TWSDLExtension extn = getExtensionOfType(part, type);
                    if (extn != null)
                        return extn;
                }
            }
        }

        return null;
    }

    // bug fix: 4857100
    protected static com.sun.tools.ws.wsdl.document.Message findMessage(
        QName messageName,
        ProcessSOAPOperationInfo info) {
        com.sun.tools.ws.wsdl.document.Message message = null;
        try {
            message =
                (com.sun.tools.ws.wsdl.document.Message)info.document.find(
                    Kinds.MESSAGE,
                    messageName);
        } catch (NoSuchEntityException e) {
        }
        return message;
    }

    protected static boolean tokenListContains(
        String tokenList,
        String target) {
        if (tokenList == null) {
            return false;
        }

        StringTokenizer tokenizer = new StringTokenizer(tokenList, " ");
        while (tokenizer.hasMoreTokens()) {
            String s = tokenizer.nextToken();
            if (target.equals(s)) {
                return true;
            }
        }
        return false;
    }

    protected String getUniqueClassName(String className) {
        int cnt = 2;
        String uniqueName = className;
        while (reqResNames.contains(uniqueName.toLowerCase())) {
            uniqueName = className + cnt;
            cnt++;
        }
        reqResNames.add(uniqueName.toLowerCase());
        return uniqueName;
    }

    private String getJavaPackageName(QName name) {
        String packageName = null;
/*        if (_modelInfo.getNamespaceMappingRegistry() != null) {
            NamespaceMappingInfo i =
                _modelInfo
                    .getNamespaceMappingRegistry()
                    .getNamespaceMappingInfo(
                    name);
            if (i != null)
                return i.getJavaPackageName();
        }*/
        return packageName;
    }

    protected boolean isConflictingClassName(String name) {
        if (_conflictingClassNames == null) {
            return false;
        }

        return _conflictingClassNames.contains(name);
    }

    protected boolean isConflictingServiceClassName(String name) {
        return isConflictingClassName(name);
    }

    protected boolean isConflictingStubClassName(String name) {
        return isConflictingClassName(name);
    }

    protected boolean isConflictingTieClassName(String name) {
        return isConflictingClassName(name);
    }

    protected boolean isConflictingPortClassName(String name) {
        return isConflictingClassName(name);
    }

    protected boolean isConflictingExceptionClassName(String name) {
        return isConflictingClassName(name);
    }

    protected static final String OPERATION_HAS_VOID_RETURN_TYPE =
        "com.sun.xml.ws.processor.modeler.wsdl.operationHasVoidReturnType";
    private static final String WSDL_DOCUMENTATION =
        "com.sun.xml.ws.processor.modeler.wsdl.documentation";
    protected static final String WSDL_PARAMETER_ORDER =
        "com.sun.xml.ws.processor.modeler.wsdl.parameterOrder";
    public static final String WSDL_RESULT_PARAMETER =
        "com.sun.xml.ws.processor.modeler.wsdl.resultParameter";
    public static final String MESSAGE_HAS_MIME_MULTIPART_RELATED_BINDING =
        "com.sun.xml.ws.processor.modeler.wsdl.mimeMultipartRelatedBinding";


    public ProcessorEnvironment getProcessorEnvironment(){
        return _env;
    }
    protected ProcessSOAPOperationInfo info;

    protected WSDLModelInfo _modelInfo;
    protected Properties _options;
    protected LocalizableMessageFactory _messageFactory;
    private Set _conflictingClassNames;
    protected Map<String,JavaException> _javaExceptions;
    protected Map _faultTypeToStructureMap;
    private ProcessorEnvironment _env;
    protected JavaSimpleTypeCreator _javaTypes;
    protected Map<QName, Port> _bindingNameToPortMap;
    protected boolean useWSIBasicProfile = true;

    private Set<String> reqResNames;
    public class ProcessSOAPOperationInfo {

        public ProcessSOAPOperationInfo(
            Port modelPort,
            com.sun.tools.ws.wsdl.document.Port port,
            com.sun.tools.ws.wsdl.document.Operation portTypeOperation,
            BindingOperation bindingOperation,
            SOAPBinding soapBinding,
            WSDLDocument document,
            boolean hasOverloadedOperations,
            Map headers) {
            this.modelPort = modelPort;
            this.port = port;
            this.portTypeOperation = portTypeOperation;
            this.bindingOperation = bindingOperation;
            this.soapBinding = soapBinding;
            this.document = document;
            this.hasOverloadedOperations = hasOverloadedOperations;
            this.headers = headers;
        }

        public Port modelPort;
        public com.sun.tools.ws.wsdl.document.Port port;
        public com.sun.tools.ws.wsdl.document.Operation portTypeOperation;
        public BindingOperation bindingOperation;
        public SOAPBinding soapBinding;
        public WSDLDocument document;
        public boolean hasOverloadedOperations;
        public Map headers;

        // additional data
        public Operation operation;
        public String uniqueOperationName;
    }

    public static class WSDLExceptionInfo {
        public String exceptionType;
        public QName wsdlMessage;
        public String wsdlMessagePartName;
        public HashMap constructorOrder; // mapping of element name to
                                             // constructor order (of type Integer)
    }


    protected WSDLParser parser;
    protected WSDLDocument document;
    protected HashSet hSet;
}
