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
 * Copyright 2007 Sun Microsystems Inc. All Rights Reserved
 */
package com.sun.tools.ws.processor.model;

import java.util.Iterator;

/**
 *
 * A model visitor incorporating all the logic required to walk through the model.
 *
 * @author WS Development Team
 */
public class ExtendedModelVisitor {

    public ExtendedModelVisitor() {}

    public void visit(Model model) throws Exception {
        preVisit(model);
        for (Service service : model.getServices()) {
            preVisit(service);
            for (Port port : service.getPorts()) {
                preVisit(port);
                if (shouldVisit(port)) {
                    for (Operation operation : port.getOperations()) {                        
                        preVisit(operation);
                        Request request = operation.getRequest();
                        if (request != null) {
                            preVisit(request);
                            for (Iterator iter4 = request.getHeaderBlocks();
                                iter4.hasNext();) {

                                Block block = (Block) iter4.next();
                                visitHeaderBlock(block);
                            }
                            for (Iterator iter4 = request.getBodyBlocks();
                                iter4.hasNext();) {

                                Block block = (Block) iter4.next();
                                visitBodyBlock(block);
                            }
                            for (Iterator iter4 = request.getParameters();
                                iter4.hasNext();) {

                                Parameter parameter = (Parameter) iter4.next();
                                visit(parameter);
                            }
                            postVisit(request);
                        }

                        Response response = operation.getResponse();
                        if (response != null) {
                            preVisit(response);
                            for (Iterator iter4 = response.getHeaderBlocks();
                                iter4.hasNext();) {

                                Block block = (Block) iter4.next();
                                visitHeaderBlock(block);
                            }
                            for (Iterator iter4 = response.getBodyBlocks();
                                iter4.hasNext();) {

                                Block block = (Block) iter4.next();
                                visitBodyBlock(block);
                            }
                            for (Iterator iter4 = response.getParameters();
                                iter4.hasNext();) {

                                Parameter parameter = (Parameter) iter4.next();
                                visit(parameter);
                            }
                            postVisit(response);
                        }

                        for (Iterator iter4 = operation.getFaults();
                            iter4.hasNext();) {

                            Fault fault = (Fault) iter4.next();
                            preVisit(fault);
                            visitFaultBlock(fault.getBlock());
                            postVisit(fault);
                        }
                        postVisit(operation);
                    }
                }
                postVisit(port);
            }
            postVisit(service);
        }
        postVisit(model);
    }

    protected boolean shouldVisit(Port port) {
        return true;
    }

    // these methods are intended for subclasses
    protected void preVisit(Model model) throws Exception {}
    protected void postVisit(Model model) throws Exception {}
    protected void preVisit(Service service) throws Exception {}
    protected void postVisit(Service service) throws Exception {}
    protected void preVisit(Port port) throws Exception {}
    protected void postVisit(Port port) throws Exception {}
    protected void preVisit(Operation operation) throws Exception {}
    protected void postVisit(Operation operation) throws Exception {}
    protected void preVisit(Request request) throws Exception {}
    protected void postVisit(Request request) throws Exception {}
    protected void preVisit(Response response) throws Exception {}
    protected void postVisit(Response response) throws Exception {}
    protected void preVisit(Fault fault) throws Exception {}
    protected void postVisit(Fault fault) throws Exception {}
    protected void visitBodyBlock(Block block) throws Exception {}
    protected void visitHeaderBlock(Block block) throws Exception {}
    protected void visitFaultBlock(Block block) throws Exception {}
    protected void visit(Parameter parameter) throws Exception {}
}
