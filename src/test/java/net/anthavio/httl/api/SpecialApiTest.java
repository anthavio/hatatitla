package net.anthavio.httl.api;

import java.io.IOException;
import java.util.Date;

import net.anthavio.httl.Constants;
import net.anthavio.httl.RequestInterceptor;
import net.anthavio.httl.ResponseInterceptor;
import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.SenderResponse;
import net.anthavio.httl.api.ComplexApiTest.SomeBean;
import net.anthavio.httl.inout.RequestBodyMarshaller;
import net.anthavio.httl.inout.ResponseBodyExtractor;
import net.anthavio.httl.util.MockSender;

import org.fest.assertions.api.Assertions;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.testng.annotations.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class SpecialApiTest {

	@Test
	public void interceptors() throws IOException {

		// Given
		MockSender sender = new MockSender();
		SpecialApi api = ApiBuilder.with(sender).build(SpecialApi.class);

		SomeBean bean = new SomeBean("Quido Guido", new Date(), 999);
		String json = sender.getRequestMarshaller("application/json").marshall(bean);
		// When
		MockRequestInterceptor reqinc = new MockRequestInterceptor();
		MockResponseInterceptor resinc = new MockResponseInterceptor();
		String returned = api.intercept(bean, reqinc, resinc);
		// Then
		Assertions.assertThat(reqinc.getLastRequest()).isEqualTo(sender.getLastRequest());
		Assertions.assertThat(resinc.getLastResponse()).isEqualTo(sender.getLastResponse());
		Assertions.assertThat(sender.getLastRequest().getFirstHeader(Constants.Content_Type))
				.startsWith("application/json");
		Assertions.assertThat(sender.getLastResponse().getFirstHeader(Constants.Content_Type)).startsWith(
				"application/json");

		Assertions.assertThat(returned).isEqualTo(json);
	}

	@Test
	public void customSetter() {
		// Given
		MockSender sender = new MockSender();
		SpecialApi api = ApiBuilder.with(sender).build(SpecialApi.class);

		// When
		SenderResponse response = api.customSetter(new PageRequest(3, 13));

		// Then
		Assertions.assertThat(response).isEqualTo(sender.getLastResponse());
		Assertions.assertThat(sender.getLastRequest().getParameters().getFirst("xpage.number")).isEqualTo("3");
		Assertions.assertThat(sender.getLastRequest().getParameters().getFirst("xpage.size")).isEqualTo("13");
		Assertions.assertThat(sender.getLastRequest().getParameters().getFirst("xpage.sort")).isNull(); //not present
	}

	static interface SpecialApi {

		@Operation("POST /intercept")
		String intercept(@Body("application/json") SomeBean bean, RequestInterceptor requestInterceptor,
				ResponseInterceptor responseInterceptor);

		@Operation("POST /extractor")
		String extractor(ResponseBodyExtractor<Date> extractor, @Body("application/xml") SomeBean bean);

		@Operation("POST /marshaller")
		@Headers("Content-Type: application/xml")
		String marshaller(RequestBodyMarshaller marshaller, @Body Object body);

		@Operation("GET /customSetter")
		SenderResponse customSetter(@Param(value = "xpage", setter = PageableSetter.class) Pageable pager);

		@Operation("POST /everything")
		SomeBean everything(@Param(value = "page", setter = PageableSetter.class) Pageable pager,
				RequestBodyMarshaller marshaller, @Body("application/json") Object body, ResponseBodyExtractor<Date> extractor,
				RequestInterceptor requestInterceptor, ResponseInterceptor responseInterceptor);
	}

	static class MockRequestInterceptor implements RequestInterceptor {

		private SenderRequest lastRequest;

		@Override
		public void onRequest(SenderRequest request) {
			this.lastRequest = request;
		}

		public SenderRequest getLastRequest() {
			return lastRequest;
		}
	}

	static class MockResponseInterceptor implements ResponseInterceptor {

		private SenderResponse lastResponse;

		@Override
		public void onResponse(SenderResponse response) {
			this.lastResponse = response;
		}

		public SenderResponse getLastResponse() {
			return lastResponse;
		}

	}

	static class PageableSetter implements ParamSetter<Pageable> {

		@Override
		public void set(Pageable value, String name, SenderRequest request) {
			request.addParameter(name + ".number", value.getPageNumber());
			request.addParameter(name + ".size", value.getPageSize());
			request.addParameter(name + ".sort", value.getSort());
		}

	}
}
