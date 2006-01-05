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

import javax.jws.WebParam;

/**
 * Defines parameter mode, IN, OUT or INOUT
 *
 * @author Vivek Pandey
 */

public enum Mode {
    IN(0), OUT(1), INOUT(2);

    private Mode(int mode){
        this.mode = mode;
    }
    private final int mode;

    public static Mode from(WebParam.Mode m) {
        switch(m) {
        case IN:    return IN;
        case OUT:   return OUT;
        case INOUT: return INOUT;
        }
        throw new AssertionError();
    }
}
