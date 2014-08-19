package net.anthavio.httl;

import java.io.IOException;

import net.anthavio.httl.util.MockTransConfig;

import org.assertj.core.api.Assertions;
import org.junit.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class ExecutionChainTest {

	@Test
	public void test() {

		//Given
		TestExecutionFilter interceptor = new TestExecutionFilter();
		HttlSender sender = new MockTransConfig().sender().addExecutionFilter(interceptor).build();

		//When
		HttlRequest request = sender.GET("/").build();
		HttlResponse response = sender.execute(request);
		//Then
		Assertions.assertThat(response).isNotNull();
		Assertions.assertThat(interceptor.request).isSameAs(request);
		Assertions.assertThat(interceptor.response).isSameAs(response);

		//When
		interceptor.exception = new ArrayIndexOutOfBoundsException("What!?!");
		try {
			sender.execute(request);
			Assertions.fail("Expected " + ArrayIndexOutOfBoundsException.class.getName());
		} catch (ArrayIndexOutOfBoundsException isx) {
			//Then
			Assertions.assertThat(isx).isSameAs(interceptor.exception);
		}
		sender.close();
	}
}

class TestExecutionFilter implements HttlExecutionFilter {

	public HttlRequest request;

	public HttlResponse response;

	public RuntimeException exception;

	@Override
	public HttlResponse filter(HttlRequest request, HttlExecutionChain chain) throws IOException {
		this.request = request; //record request

		if (exception != null) {
			throw exception;
		}
		response = chain.next(request); //record response
		return response;
	}

}
