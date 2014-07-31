package net.anthavio.httl;

import java.io.IOException;

import net.anthavio.httl.util.MockSenderConfig;

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
		TestExecutionInterceptor interceptor = new TestExecutionInterceptor();
		HttlSender sender = new MockSenderConfig().addExecutionInterceptor(interceptor).build();

		//When
		HttlRequest request = sender.GET("/").build();
		HttlResponse response = sender.execute(request);
		//Then
		Assertions.assertThat(response).isNotNull();
		Assertions.assertThat(interceptor.request).isSameAs(request);
		Assertions.assertThat(interceptor.response).isSameAs(response);

		//When
		interceptor.exception = new IllegalStateException("What!?!");
		try {
			sender.execute(request);
			Assertions.fail("IllegalStateException expected");
		} catch (IllegalStateException isx) {
			//Then
			Assertions.assertThat(isx).isSameAs(interceptor.exception);
		}
		sender.close();
	}
}

class TestExecutionInterceptor implements HttlExecutionInterceptor {

	public HttlRequest request;

	public HttlResponse response;

	public RuntimeException exception;

	@Override
	public HttlResponse intercept(HttlRequest request, HttlExecutionChain chain) throws IOException {
		this.request = request; //record request

		if (exception != null) {
			throw exception;
		}
		response = chain.next(request); //record response
		return response;
	}

}
