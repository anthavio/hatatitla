package net.anthavio.httl.jaxrs;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Configuration;

import org.assertj.core.api.Assertions;
import org.testng.annotations.Test;

/**
 * https://jersey.java.net/documentation/latest/client.html
 * http://docs.oracle.com/javaee/7/tutorial/doc/jaxrs-client001.htm#BABBIHEJ
 * http://cxf.apache.org/docs/jax-rs-client-api.html
 * 
 * @author vanek
 *
 */
public class JaxRsTest {

	@Test
	public void test() {
		HttlConfig configuration1 = new HttlConfig();
		Client client = ClientBuilder.newClient(configuration1);
		Configuration configuration2 = client.getConfiguration();
		Assertions.assertThat(configuration2).isEqualTo((Configuration) configuration1);
		client.target("http://localhost:8080/rs");
		Assertions.assertThat(client).isInstanceOf(HttlClient.class);
	}
}
