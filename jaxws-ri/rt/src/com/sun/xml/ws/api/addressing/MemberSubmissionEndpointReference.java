package com.sun.xml.ws.api.addressing;

import com.sun.istack.NotNull;
import com.sun.xml.ws.addressing.v200408.MemberSubmissionAddressingConstants;
import org.w3c.dom.Element;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.ws.EndpointReference;
import javax.xml.ws.WebServiceException;
import java.util.List;
import java.util.Map;

/**
 * Data model for Member Submission WS-Addressing specification. This is modeled after the
 * member submission schema at:
 *
 *  http://schemas.xmlsoap.org/ws/2004/08/addressing/
 *
 * @author Kathy Walsh
 * @author Vivek Pandey
 */

@XmlRootElement(name = "EndpointReference", namespace = MemberSubmissionEndpointReference.MSNS)
@XmlType(name = "EndpointReferenceType", namespace = MemberSubmissionEndpointReference.MSNS)
public class MemberSubmissionEndpointReference extends EndpointReference implements MemberSubmissionAddressingConstants {

    private final static JAXBContext msjc = MemberSubmissionEndpointReference.getMSJaxbContext();
    private Marshaller marshaller;
    private Unmarshaller unmarshaller;

    public MemberSubmissionEndpointReference() {
    }

    /**
     * construct an EPR from infoset representation
     *
     * @param source A source object containing valid XmlInfoset
     *               instance consistent with the Member Submission WS-Addressing
     * @throws javax.xml.ws.WebServiceException
     *                              if the source does not contain a valid W3C WS-Addressing
     *                              EndpointReference.
     * @throws WebServiceException if the <code>null</code> <code>source</code> value is given
     */
    public MemberSubmissionEndpointReference(@NotNull Source source) {

        if (source == null)
            throw new WebServiceException("Source parameter can not be null on constructor");

        try {
            if (unmarshaller == null)
                unmarshaller = MemberSubmissionEndpointReference.msjc.createUnmarshaller();
            MemberSubmissionEndpointReference epr = (MemberSubmissionEndpointReference) unmarshaller.unmarshal(source);

            this.addr = epr.addr;
            this.referenceProperties = epr.referenceProperties;
            this.referenceParameters = epr.referenceParameters;
            this.portTypeName = epr.portTypeName;
            this.serviceName = epr.serviceName;
            this.attributes = epr.attributes;
            this.elements = epr.elements;
        } catch (JAXBException e) {
            throw new WebServiceException("Error unmarshalling MemberSubmissionEndpointReference ", e);
        } catch (ClassCastException e) {
            throw new WebServiceException("Source did not contain MemberSubmissionEndpointReference", e);
        }
    }

    public void writeTo(Result result) {
        try {
            if (marshaller == null)
                marshaller = MemberSubmissionEndpointReference.msjc.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            marshaller.marshal(this, result);
        } catch (JAXBException e) {
            throw new WebServiceException("Error marshalling W3CEndpointReference. ", e);
        }
    }

    private static JAXBContext getMSJaxbContext() {
        try {
            return JAXBContext.newInstance(MemberSubmissionEndpointReference.class);
        } catch (JAXBException e) {
            throw new WebServiceException("Error creating JAXBContext for MemberSubmissionEndpointReference. ", e);
        }
    }

    @XmlElement(name = "Address", namespace = MemberSubmissionEndpointReference.MSNS)
    public Address addr;

    @XmlElement(name = "ReferenceProperties", namespace = MemberSubmissionEndpointReference.MSNS)
    public Elements referenceProperties;

    @XmlElement(name = "ReferenceParameters", namespace = MemberSubmissionEndpointReference.MSNS)
    public Elements referenceParameters;

    @XmlElement(name = "PortType", namespace = MemberSubmissionEndpointReference.MSNS)
    public AttributedQName portTypeName;

    @XmlElement(name = "ServiceName", namespace = MemberSubmissionEndpointReference.MSNS)
    public ServiceNameType serviceName;

    @XmlAnyAttribute
    public Map<QName,String> attributes;

    @XmlAnyElement
    public List<Element> elements;

    public static class Address {
        public Address() {
        }

        @XmlValue
        public String uri;
        @XmlAnyAttribute
        public Map<QName, String> attributes;
    }

    public static class Elements {
        public Elements() {}

        @XmlAnyElement
        public List<Element> elements;
    }


    public static class AttributedQName {
        public AttributedQName() {
        }

        @XmlValue
        public QName name;
        @XmlAnyAttribute
        public Map<QName, String> attributes;
    }

    public static class ServiceNameType extends AttributedQName{
        public ServiceNameType() {
        }

        @XmlAttribute(name="PortName")
        public String portName;
    }

    protected static final String MSNS = "http://schemas.xmlsoap.org/ws/2004/08/addressing";
}
