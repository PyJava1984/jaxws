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
package com.sun.xml.ws.server;

import com.sun.xml.ws.encoding.jaxb.JAXBBeanInfo;
import com.sun.xml.ws.encoding.jaxb.JAXBTypeSerializer;
import com.sun.xml.ws.encoding.jaxb.LogicalEncoder;

import javax.xml.bind.JAXBContext;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;

public class LogicalEncoderImpl implements LogicalEncoder {

    /*
     * Unmarshall source to JAXB bean using context
     * @see LogicalEncoder#toJAXBBean(Source, JAXBContext)
     */
    public JAXBBeanInfo toJAXBBeanInfo(Source source, JAXBContext context) {
        Object obj = JAXBTypeSerializer.getInstance().deserialize(source, context);
        return new JAXBBeanInfo(obj, context);
    }

    /*
     */
    public DOMSource toDOMSource(JAXBBeanInfo beanInfo) {
        return JAXBTypeSerializer.getInstance().serialize(beanInfo.getBean(),
                beanInfo.getJAXBContext());
    }

}
