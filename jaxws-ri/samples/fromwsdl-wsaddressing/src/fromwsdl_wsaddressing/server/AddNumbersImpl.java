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
package fromwsdl_wsaddressing.server;

import javax.annotation.Resource;
import javax.jws.WebService;
import javax.xml.ws.WebServiceContext;

@WebService(endpointInterface = "fromwsdl_wsaddressing.server.AddNumbersPortType")
public class AddNumbersImpl implements AddNumbersPortType {

    @Resource
    WebServiceContext wsc;

    public int addNumbers(int number1, int number2)
            throws AddNumbersFault_Exception {
        return impl(number1, number2);
    }

    public int addNumbers2(int number1, int number2)
            throws AddNumbersFault_Exception {
        return impl(number1, number2);
    }

    public int addNumbers3(int number1, int number2)
            throws AddNumbersFault_Exception {
        return impl(number1, number2);
    }

    int impl(int number1, int number2) throws AddNumbersFault_Exception {
        if (number1 < 0 || number2 < 0) {
            AddNumbersFault fb = new AddNumbersFault();
            fb.setDetail("Negative numbers cant be added!");
            fb.setMessage("Numbers: " + number1 + ", " + number2);

            throw new AddNumbersFault_Exception(fb.getMessage(), fb);
        }

        return number1 + number2;
    }
}
