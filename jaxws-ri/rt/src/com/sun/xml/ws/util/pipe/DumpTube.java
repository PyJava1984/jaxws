package com.sun.xml.ws.util.pipe;

import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.api.pipe.NextAction;
import com.sun.xml.ws.api.pipe.Pipe;
import com.sun.xml.ws.api.pipe.PipeCloner;
import com.sun.xml.ws.api.pipe.Tube;
import com.sun.xml.ws.api.pipe.helper.AbstractFilterTubeImpl;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.PrintStream;
import java.lang.reflect.Constructor;

/**
 * {@link Pipe} that dumps messages that pass through.
 *
 * @author Kohsuke Kawaguchi
 */
public class DumpTube extends AbstractFilterTubeImpl {

    private final String name;

    private final PrintStream out;

    private final XMLOutputFactory staxOut;

    /**
     * @param name
     *      Specify the name that identifies this {@link DumpTube}
     *      instance. This string will be printed when this pipe
     *      dumps messages, and allows people to distinguish which
     *      pipe instance is dumping a message when multiple
     *      {@link DumpTube}s print messages out.
     * @param out
     *      The output to send dumps to.
     * @param next
     *      The next {@link Tube} in the pipeline.
     */
    public DumpTube(String name, PrintStream out, Tube next) {
        super(next);
        this.name = name;
        this.out = out;
        this.staxOut = XMLOutputFactory.newInstance();
        //staxOut.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES,true);
    }

    /**
     * Copy constructor.
     */
    protected DumpTube(DumpTube that, PipeCloner cloner) {
        super(that,cloner);
        this.name = that.name;
        this.out = that.out;
        this.staxOut = that.staxOut;
    }

    public NextAction processRequest(Packet request) {
        dump("request",request);
        return super.processRequest(request);
    }

    public NextAction processResponse(Packet response) {
        dump("response",response);
        return super.processResponse(response);
    }

    private void dump(String header, Packet packet) {
        out.println("====["+name+":"+header+"]====");
        if(packet.getMessage()==null)
            out.println("(none)");
        else
            try {
                XMLStreamWriter writer = staxOut.createXMLStreamWriter(new PrintStream(out) {
                    public void close() {
                        // noop
                    }
                });
                writer = createIndenter(writer);
                packet.getMessage().copy().writeTo(writer);
                writer.close();
            } catch (XMLStreamException e) {
                e.printStackTrace(out);
            }
        out.println("============");
    }

    /**
     * Wraps {@link XMLStreamWriter} by an indentation engine if possible.
     *
     * <p>
     * We can do this only when we have <tt>stax-utils.jar</tt> in the classpath.
     */
    private XMLStreamWriter createIndenter(XMLStreamWriter writer) {
        try {
            Class clazz = getClass().getClassLoader().loadClass("javanet.staxutils.IndentingXMLStreamWriter");
            Constructor c = clazz.getConstructor(XMLStreamWriter.class);
            writer = (XMLStreamWriter)c.newInstance(writer);
        } catch (Exception e) {
            // if stax-utils.jar is not in the classpath, this will fail
            // so, we'll just have to do without indentation
            if(!warnStaxUtils) {
                warnStaxUtils = true;
                out.println("WARNING: put stax-utils.jar to the classpath to indent the dump output");
            }
        }
        return writer;
    }


    public DumpTube copy(PipeCloner cloner) {
        return new DumpTube(this,cloner);
    }

    public void preDestroy() {
        // noop
    }

    private static boolean warnStaxUtils;
}
