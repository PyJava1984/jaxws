
package fromjava.nosei_rpclit_apt;


public class Fault2 extends Exception {
    
    int age;

    public Fault2 (String message, int age) {
	  super(message);
        this.age = age;
    }

    public int getAge() {
        return age;
    }
}
