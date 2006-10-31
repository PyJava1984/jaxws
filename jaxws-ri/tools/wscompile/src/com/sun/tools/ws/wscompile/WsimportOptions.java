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

package com.sun.tools.ws.wscompile;

import com.sun.codemodel.JCodeModel;
import com.sun.tools.ws.resources.ConfigurationMessages;
import com.sun.tools.ws.resources.WscompileMessages;
import com.sun.tools.ws.util.ForkEntityResolver;
import com.sun.tools.ws.wsdl.document.jaxws.JAXWSBindingsConstants;
import com.sun.tools.xjc.reader.Util;
import com.sun.xml.ws.streaming.XMLStreamReaderFactory;
import com.sun.xml.ws.streaming.XMLStreamReaderUtil;
import com.sun.xml.ws.util.JAXWSUtils;
import com.sun.xml.ws.util.xml.XmlUtil;
import org.w3c.dom.Element;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.LocatorImpl;

import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Vivek Pandey
 */
public class WsimportOptions extends Options {
    /**
     * -wsdlLocation
     */
    public String wsdlLocation;

    /**
     * Actually stores {@link com.sun.org.apache.xml.internal.resolver.tools.CatalogResolver}, but the field
     * type is made to {@link org.xml.sax.EntityResolver} so that XJC can be
     * used even if resolver.jar is not available in the classpath.
     */
    public EntityResolver entityResolver = null;

    /**
     * The -p option that should control the default Java package that
     * will contain the generated code. Null if unspecified.
     */
    public String defaultPackage = null;

    public JCodeModel getCodeModel() {
        if(codeModel == null)
            codeModel = new JCodeModel();
        return codeModel;
    }

    public void setCodeModel(JCodeModel codeModel) {
        this.codeModel = codeModel;
    }

    private JCodeModel codeModel;

    @Override
    public int parseArguments(String[] args, int i) throws BadCommandLineException {
        int j = super.parseArguments(args ,i);
        if (args[i].equals("-b")) {
            addBindings(requireArgument("-b", args, ++i));
            return 2;
        } else if (args[i].equals("-wsdllocation")) {
            wsdlLocation = requireArgument("-wsdllocation", args, ++i);
            return 2;
        } else if (args[i].equals("-p")) {
            defaultPackage = requireArgument("-p", args, ++i);
            return 2;
        } else if (args[i].equals("-catalog")) {
            String catalog = requireArgument("-catalog", args, ++i);
            try {
                if (entityResolver == null) {
                    if (catalog != null && catalog.length() > 0)
                        entityResolver = XmlUtil.createEntityResolver(JAXWSUtils.getFileOrURL(catalog));
                } else if (catalog != null && catalog.length() > 0) {
                    EntityResolver er = XmlUtil.createEntityResolver(JAXWSUtils.getFileOrURL(catalog));
                    entityResolver = new ForkEntityResolver(er, entityResolver);
                }
            } catch (IOException e) {
                throw new BadCommandLineException(WscompileMessages.WSIMPORT_FAILED_TO_PARSE(catalog, e.getMessage()));
            }
            return 2;
        } else if (args[i].startsWith("-httpproxy:")) {
            String value = requireArgument("-httpproxy:", args, ++i).substring(11);
            if (value.length() == 0) {
                throw new BadCommandLineException(WscompileMessages.WSCOMPILE_INVALID_OPTION(args[i]));
            }
            int index = value.indexOf(':');
            if (index == -1) {
                System.setProperty("proxySet", "true");
                System.setProperty("proxyHost", value);
                System.setProperty("proxyPort", "8080");
            } else {
                System.setProperty("proxySet", "true");
                System.setProperty("proxyHost", value.substring(0, index));
                System.setProperty("proxyPort", value.substring(index + 1));
            }
            return 2;
        }
        return j;
    }

    public void validate() throws BadCommandLineException {
        if (wsdls.isEmpty()) {
            throw new BadCommandLineException(WscompileMessages.WSIMPORT_MISSING_FILE());
        }
        if(wsdlLocation == null){
            wsdlLocation = wsdls.get(0).getSystemId();
        }
    }


    @Override
    protected void addFile(String arg) throws BadCommandLineException {
        addFile(arg, wsdls, "*.wsdl");
    }

    private final List<InputSource> wsdls = new ArrayList<InputSource>();
    private final List<InputSource> schemas = new ArrayList<InputSource>();
    private final List<InputSource> bindingFiles = new ArrayList<InputSource>();
    private final List<InputSource> jaxwsCustomBindings = new ArrayList<InputSource>();
    private final List<InputSource> jaxbCustomBindings = new ArrayList<InputSource>();
    private final List<Element> handlerConfigs = new ArrayList<Element>();

    /**
     * There is supposed to be one handler chain per generated SEI.
     * TODO: There is possible bug, how to associate a @HandlerChain
     * with each port on the generated SEI. For now lets preserve the JAXWS 2.0 FCS
     * behaviour and generate only one @HandlerChain on the SEI
     */
    public Element getHandlerChainConfiguration(){
        if(handlerConfigs.size() > 0)
            return handlerConfigs.get(0);
        return null;
    }

    public void addHandlerChainConfiguration(Element config){
        handlerConfigs.add(config);
    }

    public InputSource[] getWSDLs() {
        return wsdls.toArray(new InputSource[wsdls.size()]);
    }

    public InputSource[] getSchemas() {
        return schemas.toArray(new InputSource[schemas.size()]);
    }

    public InputSource[] getWSDLBindings() {
        return jaxwsCustomBindings.toArray(new InputSource[jaxwsCustomBindings.size()]);
    }

    public InputSource[] getSchemaBindings() {
        return jaxbCustomBindings.toArray(new InputSource[jaxbCustomBindings.size()]);
    }

    public void addWSDL(File source) {
        addWSDL(fileToInputSource(source));
    }

    public void addWSDL(InputSource is) {
        wsdls.add(absolutize(is));
    }

    public void addSchema(File source) {
        addSchema(fileToInputSource(source));
    }

    public void addSchema(InputSource is) {
        schemas.add(is);
    }

    private InputSource fileToInputSource(File source) {
        try {
            String url = source.toURL().toExternalForm();
            return new InputSource(Util.escapeSpace(url));
        } catch (MalformedURLException e) {
            return new InputSource(source.getPath());
        }
    }

    /**
     * Recursively scan directories and add all XSD files in it.
     */
    public void addGrammarRecursive(File dir) {
        addRecursive(dir, ".wsdl", wsdls);
        addRecursive(dir, ".xsd", schemas);
    }

    /**
     * Adds a new input schema.
     */
    public void addWSDLBindFile(InputSource is) {
        jaxwsCustomBindings.add(absolutize(is));
    }

    public void addSchemmaBindFile(InputSource is) {
        jaxbCustomBindings.add(absolutize(is));
    }

    private void addRecursive(File dir, String suffix, List<InputSource> result) {
        File[] files = dir.listFiles();
        if (files == null) return; // work defensively

        for (File f : files) {
            if (f.isDirectory())
                addRecursive(f, suffix, result);
            else if (f.getPath().endsWith(suffix))
                result.add(absolutize(fileToInputSource(f)));
        }
    }

    private InputSource absolutize(InputSource is) {
        // absolutize all the system IDs in the input,
        // so that we can map system IDs to DOM trees.
        try {
            URL baseURL = new File(".").getCanonicalFile().toURL();
            is.setSystemId(new URL(baseURL, is.getSystemId()).toExternalForm());
        } catch (IOException e) {
            // ignore
        }
        return is;
    }

    public void addBindings(String name) throws BadCommandLineException {
        addFile(name, bindingFiles, null);
    }

    /**
     * Parses a token to a file (or a set of files)
     * and add them as {@link InputSource} to the specified list.
     *
     * @param suffix If the given token is a directory name, we do a recusive search
     *               and find all files that have the given suffix.
     */
    private void addFile(String name, List<InputSource> target, String suffix) throws BadCommandLineException {
        Object src;
        try {
            src = Util.getFileOrURL(name);
        } catch (IOException e) {
            throw new BadCommandLineException(WscompileMessages.WSIMPORT_NOT_A_FILE_NOR_URL(name));
        }
        if (src instanceof URL) {
            target.add(absolutize(new InputSource(Util.escapeSpace(((URL) src).toExternalForm()))));
        } else {
            File fsrc = (File) src;
            if (fsrc.isDirectory()) {
                addRecursive(fsrc, suffix, target);
            } else {
                target.add(absolutize(fileToInputSource(fsrc)));
            }
        }
    }


    /**
     * Binding files could be jaxws or jaxb. This method identifies jaxws and jaxb binding files and keeps them separately. jaxb binding files are given separately
     * to JAXB in {@link com.sun.tools.ws.processor.modeler.wsdl.JAXBModelBuilder}
     *
     * @param receiver {@link ErrorReceiver}
     */
    void parseBindings(ErrorReceiver receiver){
        for (InputSource is : bindingFiles) {
            XMLStreamReader reader =
                    XMLStreamReaderFactory.createFreshXMLStreamReader(is, true);
            XMLStreamReaderUtil.nextElementContent(reader);
            if (reader.getName().equals(JAXWSBindingsConstants.JAXWS_BINDINGS)) {
                jaxwsCustomBindings.add(is);
            } else if (reader.getName().equals(JAXWSBindingsConstants.JAXB_BINDINGS)) {
                jaxbCustomBindings.add(is);
            } else {
                LocatorImpl locator = new LocatorImpl();
                locator.setSystemId(reader.getLocation().getSystemId());
                locator.setPublicId(reader.getLocation().getPublicId());
                locator.setLineNumber(reader.getLocation().getLineNumber());
                locator.setColumnNumber(reader.getLocation().getColumnNumber());
                receiver.warning(locator, ConfigurationMessages.CONFIGURATION_NOT_BINDING_FILE(is.getSystemId()));
            }
        }
    }

    private static final LocatorImpl NULL_LOCATOR = new LocatorImpl();


}
