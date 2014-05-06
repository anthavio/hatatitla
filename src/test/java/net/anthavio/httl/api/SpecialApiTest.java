package net.anthavio.httl.api;

import java.util.Date;

import net.anthavio.httl.RequestInterceptor;
import net.anthavio.httl.ResponseInterceptor;
import net.anthavio.httl.SenderRequest;
import net.anthavio.httl.SenderResponse;
import net.anthavio.httl.api.ComplexApiTest.SomeBean;
import net.anthavio.httl.inout.RequestBodyMarshaller;
import net.anthavio.httl.inout.ResponseBodyExtractor;
import net.anthavio.httl.util.MockSender;

import org.springframework.data.domain.Pageable;
import org.testng.annotations.Test;

/**
 * 
 * @author martin.vanek
 *
 */
public class SpecialApiTest {

	@Test
	public void test() {
		MockSender sender = new MockSender();
		SpecialApi api = ApiBuilder.with(sender).build(SpecialApi.class);

		SomeBean bean = new SomeBean("Quido Guido", new Date(), 999);

		MockRequestInterceptor reqinc = new MockRequestInterceptor();
		MockResponseInterceptor resinc = new MockResponseInterceptor();
		String string = api.intercept(bean, reqinc, resinc);

		//api.paging(new PageRequest(1, 10));
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

		@Operation("GET /paging")
		String paging(@Param(value = "page", setter = PageableSetter.class) Pageable pager);

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
