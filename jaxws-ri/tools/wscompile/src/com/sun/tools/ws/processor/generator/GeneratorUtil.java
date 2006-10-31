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

package com.sun.tools.ws.processor.generator;

import com.sun.tools.ws.processor.model.Fault;
import com.sun.tools.ws.processor.model.java.JavaStructureType;
import com.sun.tools.ws.processor.util.IndentingWriter;
import com.sun.tools.ws.wscompile.Options;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.util.Comparator;


/**
 *
 * @author WS Development Team
 */
public class GeneratorUtil implements GeneratorConstants {

    public static void writeNewQName(IndentingWriter p, QName name)
        throws IOException {
        p.p(
            "new QName(\""
                + name.getNamespaceURI()
                + "\", \""
                + name.getLocalPart()
                + "\")");
    }


    public static boolean classExists(
        Options options,
        String className) {
        try {
            // Takes care of inner classes.
            getLoadableClassName(className, options.getClassLoader());
            return true;
        } catch(ClassNotFoundException ce) {
        }
        return false;
    }

    public static String getLoadableClassName(
        String className,
        ClassLoader classLoader)
        throws ClassNotFoundException {

        try {
            Class.forName(className, true, classLoader);
        } catch (ClassNotFoundException e) {
            int idx = className.lastIndexOf(DOTC);
            if (idx > -1) {
                String tmp = className.substring(0, idx) + SIG_INNERCLASS;
                tmp += className.substring(idx + 1);
                return getLoadableClassName(tmp, classLoader);
            }
            throw e;
        }
        return className;
    }    

    public static class FaultComparator implements Comparator {
        private boolean sortName = false;
        public FaultComparator() {
        }
        public FaultComparator(boolean sortName) {
            this.sortName = sortName;
        }

        public int compare(Object o1, Object o2) {
            if (sortName) {
                QName name1 = ((Fault) o1).getBlock().getName();
                QName name2 = ((Fault) o2).getBlock().getName();
                // Faults that are processed by name first, then type 
                if (!name1.equals(name2)) {
                    return name1.toString().compareTo(name2.toString());
                }
            }
            JavaStructureType type1 = ((Fault) o1).getJavaException();
            JavaStructureType type2 = ((Fault) o2).getJavaException();
            int result = sort(type1, type2);
            return result;
        }

        protected int sort(JavaStructureType type1, JavaStructureType type2) {
            if (type1.getName().equals(type2.getName())) {
                return 0;
            }
            JavaStructureType superType;
            superType = type1.getSuperclass();
            while (superType != null) {
                if (superType.equals(type2)) {
                    return -1;
                }
                superType = superType.getSuperclass();
            }
            superType = type2.getSuperclass();
            while (superType != null) {
                if (superType.equals(type1)) {
                    return 1;
                }
                superType = superType.getSuperclass();
            }
            if (type1.getSubclasses() == null && type2.getSubclasses() != null)
                return -1;
            if (type1.getSubclasses() != null && type2.getSubclasses() == null)
                return 1;
            if (type1.getSuperclass() != null
                && type2.getSuperclass() == null) {
                return 1;
            }
            if (type1.getSuperclass() == null
                && type2.getSuperclass() != null) {
                return -1;
            }
            return type1.getName().compareTo(type2.getName());
        }
    }
}
