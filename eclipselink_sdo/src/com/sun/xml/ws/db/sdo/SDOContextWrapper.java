package com.sun.xml.ws.db.sdo;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.SchemaOutputResolver;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamSource;

import org.eclipse.persistence.Version;
import org.eclipse.persistence.sdo.helper.SDOHelperContext;

import com.sun.xml.ws.spi.db.BindingContext;
import com.sun.xml.ws.spi.db.PropertyAccessor;
import com.sun.xml.ws.spi.db.TypeInfo;
import com.sun.xml.ws.spi.db.WrapperComposite;
import com.sun.xml.ws.spi.db.XMLBridge;

import commonj.sdo.Type;
import commonj.sdo.helper.HelperContext;
import commonj.sdo.helper.TypeHelper;
import commonj.sdo.helper.XSDHelper;

/**
 * Created by IntelliJ IDEA.
 * User: giglee
 * Date: May 13, 2009
 * Time: 9:48:11 AM
 * To change this template use File | Settings | File Templates.
 */
public class SDOContextWrapper implements BindingContext {
    
    public static final String SDO_SCHEMA_INFO = "com.sun.xml.ws.db.sdo.SCHEMA_INFO";
    
    public static final String SDO_SCHEMA_FILE = "com.sun.xml.ws.db.sdo.SCHEMA_FILE";

    public static final String SDO_HELPER_CONTEXT_RESOLVER = "com.sun.xml.ws.db.sdo.HELPER_CONTEXT_RESOLVER";
    
    private Map<String, Object> properties;
    
    private Set<SchemaInfo> suppliedSchemas;
    
    private List<Source> schemas = null;

    private Map<Class<?>, SDOWrapperAccessor> wrapperAccessors;
    
    private Xsd2JavaSDOModel model;
    
    private HelperContextResolver contextResolver;
    
    private boolean isClient;
    
    private QName serviceName;
    
    private boolean initialized;
    
    private HelperContext defaultContext;

    public SDOContextWrapper(Map<String, Object> props) {
        this.properties = props;
        wrapperAccessors = new HashMap<Class<?>, SDOWrapperAccessor>();
        contextResolver = (HelperContextResolver) properties.get(SDO_HELPER_CONTEXT_RESOLVER);
        if (contextResolver == null) {
            defaultContext = SDOHelperContext.getHelperContext();
            contextResolver = new HelperContextResolver() {
                //@Override
                public HelperContext getHelperContext(boolean isClient,
                        QName serviceName, Map<String, Object> properties) {
                    return defaultContext;
                }
            };
        }
        suppliedSchemas = (Set<SchemaInfo>) properties.get(SDO_SCHEMA_INFO);
        config(suppliedSchemas);
        SDOUtils.registerSDOContext(getHelperContext(), schemas);
    }

    public void config(Set<SchemaInfo> schemas) {
        List<Source> list = new ArrayList<Source>();
        if (schemas == null) {
            return;
        }

        for (SchemaInfo schema : schemas) {
            Source src = schema.getSchemaSource();
            String systemId = schema.getSystemID();
            src.setSystemId(systemId);
            list.add(src);
        }
        init(list.iterator());
    }
    
    public HelperContext getHelperContext() {
        return contextResolver.getHelperContext(isClient, serviceName,
                properties);
    }

    public Object newWrapperInstace(Class<?> wrapperType) {
        return getHelperContext().getDataFactory()
                .create(wrapperType);
    }
    
    public HelperContext getHelperContext(HelperContextResolver resolver,
            boolean isClient, QName serviceName, Map<String, Object> properties) {
        if (!initialized) {
            throw new SDODatabindingException("uninitialized helper context");
        }
        return resolver.getHelperContext(isClient, serviceName, properties);
    }
    
    public void init(Iterator<Source> i) {
        schemas = new ArrayList<Source>();
        while (i.hasNext()) {
            Source src = i.next();
            schemas.add(src);
        }
        initialized = true;
    }

    public void init(Source primaryWsdl) {
        schemas = SDOUtils.getSchemaClosureFromWSDL(primaryWsdl);
        initialized = true;
    }

    //@Override
    public Marshaller createMarshaller() throws JAXBException {
        // TODO Auto-generated method stub
        return null;
    }

    //@Override
    public Unmarshaller createUnmarshaller() throws JAXBException {
        // TODO Auto-generated method stub
        return null;
    }

    //@Override
    public JAXBContext getJAXBContext() {
        // TODO Auto-generated method stub
        return null;
    }

    //@Override
    public boolean hasSwaRef() {
        // TODO Auto-generated method stub
        return false;
    }

    //@Override
    public QName getElementName(Object o) throws JAXBException {
        // TODO Auto-generated method stub
        return null;
    }

    //@Override
    public QName getElementName(Class o) throws JAXBException {
        // TODO Auto-generated method stub
        return null;
    }

    //@Override
    public XMLBridge createBridge(TypeInfo ref) {
        return WrapperComposite.class.equals(ref.type) ? new WrapperBond(this,
                ref) : new SDOBond(this, ref);
    }

    //@Override
    public XMLBridge createFragmentBridge() {
        return new SDOBond(this, null);
    }

    //@Override
    public <B, V> PropertyAccessor<B, V> getElementPropertyAccessor(
            Class<B> wrapperBean, String nsUri, String localName)
            throws JAXBException {
        SDOWrapperAccessor wa = wrapperAccessors.get(wrapperBean);
        if (wa == null) {
            wa = new SDOWrapperAccessor(this, wrapperBean);
            wrapperAccessors.put(wrapperBean, wa);
        }
        return wa.getPropertyAccessor(nsUri, localName);
    }

    //@Override
    public List<String> getKnownNamespaceURIs() {
        // TODO
        return new ArrayList<String>();
    }

    //@Override
    public void generateSchema(SchemaOutputResolver outputResolver)
            throws IOException {
        try {
            TransformerFactory tf = TransformerFactory.newInstance();
            Transformer tx = tf.newTransformer();
            for (SchemaInfo si : suppliedSchemas) {
                Result res = outputResolver.createOutput(si.getTargetNamespace(),
                        si.getSystemID());
                if (si.getSchemaSource() != null)
                    tx.transform(si.getSchemaSource(), res);
                else {
                    StreamSource ss = new StreamSource(si.getSchemaLocation());
                    ss.setSystemId(si.getSystemID());
                    tx.transform(si.getSchemaSource(), res);
                }
            }
        } catch (Exception e) {
            throw new IOException(e.getMessage());
        }
        SDOSchemaCompiler compiler = createSDOCompiler();
        model = compiler.bind();
    }
    
    public SDOSchemaCompiler createSDOCompiler() {
        SDOSchemaCompiler compiler = new SDOSchemaCompiler();
        for (Source s : schemas) {
            compiler.parseSchema(s);
        }
        return compiler;
    }

    //@Override
    public QName getTypeName(TypeInfo tr) {
        QName res = model.getXsdTypeName(((Class<?>) tr.type).getName());
        if (res != null)
            return res;
        HelperContext hc = contextResolver.getHelperContext(isClient, serviceName, properties);
        TypeHelper th = hc.getTypeHelper();
        Type t = th.getType((Class<?>) tr.type);
        XSDHelper helper = hc.getXSDHelper();
        String localName = helper.getLocalName(t);
        String namespaceURI = helper.getNamespaceURI(t);
        return new QName(namespaceURI == null ? "" : namespaceURI, localName);
    }

    //@Override
    public String getBuildId() {
        return Version.getBuildRevision();
    }
}
