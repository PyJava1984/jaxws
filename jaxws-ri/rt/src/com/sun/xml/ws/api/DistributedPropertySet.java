/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.xml.ws.api;

import com.sun.istack.FinalArrayList;
import com.sun.istack.NotNull;
import com.sun.istack.Nullable;
import com.sun.xml.ws.api.message.Packet;
import com.sun.xml.ws.client.RequestContext;
import com.sun.xml.ws.client.ResponseContext;

import javax.xml.ws.WebServiceContext;
import java.util.Map.Entry;
import java.util.Set;

/**
 * {@link PropertySet} that combines properties exposed from multiple
 * {@link PropertySet}s into one.
 *
 * <p>
 * This implementation allows one {@link PropertySet} to assemble
 * all properties exposed from other "satellite" {@link PropertySet}s.
 * (A satellite may itself be a {@link DistributedPropertySet}, so
 * in general this can form a tree.)
 *
 * <p>
 * This is useful for JAX-WS because the properties we expose to the application
 * are contributed by different pieces, and therefore we'd like each of them
 * to have a separate {@link PropertySet} implementation that backs up
 * the properties. For example, this allows FastInfoset to expose its
 * set of properties to {@link RequestContext} by using a strongly-typed fields.
 *
 * <p>
 * This is also useful for a client-side transport to expose a bunch of properties
 * into {@link ResponseContext}. It simply needs to create a {@link PropertySet}
 * object with methods for each property it wants to expose, and then add that
 * {@link PropertySet} to {@link Packet}. This allows property values to be
 * lazily computed (when actually asked by users), thus improving the performance
 * of the typical case where property values are not asked.
 *
 * <p>
 * A similar benefit applies on the server-side, for a transport to expose
 * a bunch of properties to {@link WebServiceContext}.
 *
 * <p>
 * To achieve these benefits, access to {@link DistributedPropertySet} is slower
 * compared to {@link PropertySet} (such as get/set), while adding a satellite
 * object is relatively fast.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class DistributedPropertySet extends PropertySet {
    /**
     * All {@link PropertySet}s that are bundled into this {@link PropertySet}.
     */
    private final FinalArrayList<PropertySet> satellites = new FinalArrayList<PropertySet>();

    public void addSatellite(@NotNull PropertySet satellite) {
        satellites.add(satellite);
    }

    public void removeSatellite(@NotNull PropertySet satellite) {
        satellites.remove(satellite);
    }

    public void copySatelliteInto(@NotNull DistributedPropertySet r) {
        r.satellites.addAll(this.satellites);
    }

    public @Nullable <T extends PropertySet> T getSatellite(Class<T> satelliteClass) {
        for (PropertySet child : satellites) {
            if (satelliteClass.isInstance(child)) {
                return satelliteClass.cast(child);
            }

            if (DistributedPropertySet.class.isInstance(child)) {
                T satellite = DistributedPropertySet.class.cast(child).getSatellite(satelliteClass);
                if (satellite != null) {
                    return satellite;
                }
            }
        }

        return null;
    }

    @Override
    public Object get(Object key) {
        // check satellites
        for (PropertySet child : satellites) {
            if(child.supports(key))
                return child.get(key);
        }

        // otherwise it must be the master
        return super.get(key);
    }

    @Override
    public Object put(String key, Object value) {
        // check satellites
        for (PropertySet child : satellites) {
            if(child.supports(key))
                return child.put(key,value);
        }

        // otherwise it must be the master
        return super.put(key,value);
    }

    @Override
    public boolean supports(Object key) {
        // check satellites
        for (PropertySet child : satellites) {
            if(child.supports(key))
                return true;
        }

        return super.supports(key);
    }

    @Override
    public Object remove(Object key) {
        // check satellites
        for (PropertySet child : satellites) {
            if(child.supports(key))
                return child.remove(key);
        }

        return super.remove(key);
    }

    @Override
    /*package*/ void createEntrySet(Set<Entry<String, Object>> core) {
        super.createEntrySet(core);
        for (PropertySet child : satellites) {
            child.createEntrySet(core);
        }
    }
}
