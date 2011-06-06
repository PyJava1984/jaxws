package fromjava.webmethod.inheritance.server;

import javax.jws.WebService;
import javax.jws.WebMethod;

/**
 * @author Jitendra Kotamraju
 */
@WebService
public class TestImpl extends TestImplBase {

    @WebMethod
    public String method3(String str) {
        return str;
    }

    // This is also a WebMethod since declaring class
    // has WebService
    public String method4(String str) {
        return str;
    }
}
