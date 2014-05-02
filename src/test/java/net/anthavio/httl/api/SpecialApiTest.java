package net.anthavio.httl.api;

import java.util.Date;

import net.anthavio.httl.RequestInterceptor;
import net.anthavio.httl.ResponseInterceptor;
import net.anthavio.httl.api.ComplexApiTest.SomeBean;
import net.anthavio.httl.inout.RequestBodyMarshaller;
import net.anthavio.httl.inout.ResponseBodyExtractor;
import net.anthavio.httl.util.MockSender;

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
	public void test() {
		MockSender sender = new MockSender();
		SpecialApi api = ApiBuilder.with(sender).build(SpecialApi.class);
		api.paging(new PageRequest(1, 10));
	}

	static interface SpecialApi {

		@Operation("POST /intercept")
		String intercept(@Body SomeBean bean, RequestInterceptor requestInterceptor, ResponseInterceptor responseInterceptor);

		@Operation("POST /extractor")
		String extractor(ResponseBodyExtractor<Date> extractor, @Body SomeBean bean);

		@Operation("POST /marshaller")
		String marshaller(RequestBodyMarshaller marshaller, @Body Object body);

		@Operation("GET /paging")
		String paging(@Param("page") Pageable pager);
	}
}
