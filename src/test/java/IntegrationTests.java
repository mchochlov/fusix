import junit.framework.Test;
import junit.framework.TestSuite;


public class IntegrationTests {

	public static Test suite() {
		TestSuite suite = new TestSuite(IntegrationTests.class.getName());
		//$JUnit-BEGIN$

		//$JUnit-END$
		return suite;
	}

}
