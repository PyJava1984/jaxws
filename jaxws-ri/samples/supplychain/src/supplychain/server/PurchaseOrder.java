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

package supplychain.server;

import javax.xml.bind.annotation.XmlElement;

import java.util.List;
import java.util.ArrayList;

public class PurchaseOrder {
    String orderNumber;
    String customerNumber;
    @XmlElement (nillable = true)
    List<Item> itemList;
    
    public String getOrderNumber () { return orderNumber; }
    public void setOrderNumber (String orderNumber) { this.orderNumber = orderNumber; }
    
    public String getCustomerNumber () { return customerNumber; }
    public void setCustomerNumber (String customerNumber) { this.customerNumber = customerNumber; }
    
    public List<Item> getItemList () {
        if (itemList == null)
            itemList = new ArrayList<Item>();
        
        return itemList;
    }
}