package net.anthavio.httl.api;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Date;

import net.anthavio.httl.HttlBodyMarshaller;
import net.anthavio.httl.HttlBuilderVisitor;
import net.anthavio.httl.HttlConstants;
import net.anthavio.httl.HttlExecutionChain;
import net.anthavio.httl.HttlExecutionInterceptor;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequestBuilders.HttlRequestBuilder;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.api.ComplexApiTest.SomeBodyBean;
import net.anthavio.httl.marshall.Marshallers;
import net.anthavio.httl.util.MockSenderConfig;
import net.anthavio.httl.util.MockTransport;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * 
 * @author martin.vanek
 *
 */
public class SpecialApiTest {

	@Test
	public void interceptors() throws IOException {

		// Given
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockSenderConfig(transport).build();
		SpecialApi api = HttlApiBuilder.with(sender).build(SpecialApi.class);

		SomeBodyBean bean = new SomeBodyBean("Kvído Vymětal", new Date(), 999);
		String json = Marshallers.marshall(sender.getConfig().getMarshaller(), bean);
		// When
		MockBuilderInterceptor bldinc = new MockBuilderInterceptor();
		MockExecutionInterceptor exeinc = new MockExecutionInterceptor();
		String returned = api.intercept(bean, bldinc, exeinc);

		// Then
		Assertions.assertThat(exeinc.getLastRequest().getParameters().getFirst("dynamic")).isEqualTo("value");

		Assertions.assertThat(exeinc.getLastResponse()).isEqualTo(transport.getLastResponse());
		Assertions.assertThat(transport.getLastRequest().getFirstHeader(HttlConstants.Content_Type)).startsWith(
				"application/json");
		Assertions.assertThat(transport.getLastResponse().getFirstHeader(HttlConstants.Content_Type)).startsWith(
				"application/json");

		Assertions.assertThat(returned).isEqualTo(json);
	}

	@Test
	public void customSetter() {
		// Given
		MockTransport transport = new MockTransport();
		HttlSender sender = new MockSenderConfig(transport).build();
		SpecialApi api = HttlApiBuilder.with(sender).build(SpecialApi.class);

		// When
		HttlResponse response = api.customSetter(new PageRequest(3, 13));

		// Then
		Assertions.assertThat(response).isEqualTo(transport.getLastResponse());
		Assertions.assertThat(transport.getLastRequest().getParameters().getFirst("xpage.number")).isEqualTo("3");
		Assertions.assertThat(transport.getLastRequest().getParameters().getFirst("xpage.size")).isEqualTo("13");
		Assertions.assertThat(transport.getLastRequest().getParameters().getFirst("xpage.sort")).isNull(); //not present
	}

	@Test
	public void responseExtractor() throws IOException {
		// Given
		HttlSender sender = new MockSenderConfig().build();
		SpecialApi api = HttlApiBuilder.with(sender).build(SpecialApi.class);
		final SomeBodyBean bean = new SomeBodyBean("Kvído Vymětal", new Date(), 999);
		//String bodyXml = Marshallers.marshall(sender.getConfig().getRequestMarshaller("application/xml"), bean);

		HttlResponseExtractor<Date> extractor = new DateExtractor(bean.getDate());
		Date returnedDate = api.extractor(extractor, bean);
		// Then
		Assertions.assertThat(returnedDate).isEqualTo(bean.getDate());
	}

	@Test
	public void wrongTypeExctractor() {
		// Given
		HttlSender sender = new MockSenderConfig().build();
		SpecialApi api = HttlApiBuilder.with(sender).build(SpecialApi.class);
		final SomeBodyBean bean = new SomeBodyBean("Kvído Vymětal", new Date(), 999);

		// Given - Extractor is anonymous class -> we cannot get actual generic parameter type via reflection
		HttlResponseExtractor<Date> extractor = new HttlResponseExtractor<Date>() {

			@Override
			public Date extract(HttlResponse response) throws IOException {
				return bean.getDate();
			}

			@Override
			public HttlResponseExtractor<Date> supports(HttlResponse response) {
				return this;
			}

		};

		// When - return String + extractor Date
		try {
			String ouchDate = api.wrongExtractorType(bean, extractor);
			// Then
			//Assertions.fail("Expected " + HttlProcessingException.class.getName());
			Assertions.fail("Expected " + ClassCastException.class.getName());
		} catch (ClassCastException x) {
			Assertions.assertThat(x.getMessage()).isEqualTo("java.util.Date cannot be cast to java.lang.String");
		}

		// Given - DateExtractor is normal class
		// Then - we detect incompatible extractor and return type when building API
		try {
			HttlApiBuilder.with(sender).build(WrongExtractorApi.class);
			Assertions.fail("Expected " + HttlApiException.class.getName());
		} catch (HttlApiException x) {
			Assertions.assertThat(x.getMessage()).startsWith("Incompatible Extractor type:");
		}

	}

	static interface WrongExtractorApi {

		@RestCall("POST /extractorWrong")
		String wrongExtractorType(@RestBody("application/xml") SomeBodyBean bean, DateExtractor extractor);
	}

	static class DateExtractor implements HttlResponseExtractor<Date> {

		private Date date;

		public DateExtractor(Date date) {
			this.date = date;
		}

		@Override
		public HttlResponseExtractor<Date> supports(HttlResponse response) {
			return this;
		}

		@Override
		public Date extract(HttlResponse response) throws IOException {
			return date;
		}

	}

	@Test
	public void requestMarshaller() throws IOException {
		// Given
		HttlSender sender = new MockSenderConfig().build();
		SpecialApi api = HttlApiBuilder.with(sender).build(SpecialApi.class);
		SomeBodyBean bean = new SomeBodyBean("Kvído Vymětal", new Date(), 999);
		//String bodyXml = sender.getRequestMarshaller("application/xml").marshall(bean);

		// When
		HttlBodyMarshaller marshaller = new HttlBodyMarshaller() {

			@Override
			public void write(Object requestBody, OutputStream stream, Charset charset) throws IOException {
				stream.write(((SomeBodyBean) requestBody).getName().getBytes("utf-8"));
			}

			@Override
			public HttlBodyMarshaller supports(HttlRequest request) {
				return this;
			}

		};
		String returned = api.marshaller(marshaller, bean);

		// Then
		Assertions.assertThat(returned).isEqualTo(bean.getName());

	}

	static interface SpecialApi {

		@RestCall("POST /intercept")
		String intercept(@RestBody("application/json") SomeBodyBean bean, HttlBuilderVisitor builderInterceptor,
				HttlExecutionInterceptor executionInterceptor);

		@RestCall("POST /extractor")
		Date extractor(HttlResponseExtractor<Date> extractor, @RestBody("application/xml") SomeBodyBean bean);

		@RestCall("POST /extractorSilly")
		String wrongExtractorType(@RestBody("application/xml") SomeBodyBean bean, HttlResponseExtractor<Date> extractor);

		@RestCall("POST /marshaller")
		@RestHeaders("Content-Type: application/xml")
		String marshaller(HttlBodyMarshaller marshaller, @RestBody Object body);

		@RestCall("GET /customSetter")
		HttlResponse customSetter(@RestVar(name = "xpage", setter = PageableSetter.class) Pageable pager);

		@RestCall("POST /everything")
		SomeBodyBean everything(@RestVar(name = "page", setter = PageableSetter.class) Pageable pager,
				HttlBodyMarshaller marshaller, @RestBody("application/json") Object body, HttlResponseExtractor extractor,
				HttlBuilderVisitor builderInterceptor, HttlExecutionInterceptor executionInterceptor);
	}

	static class MockBuilderInterceptor implements HttlBuilderVisitor {

		private HttlRequestBuilder builder;

		public HttlRequestBuilder getLastBuilder() {
			return builder;
		}

		@Override
		public void visit(HttlRequestBuilder<?> builder) {
			builder.param("dynamic", "value");
			this.builder = builder;
		}
	}

	static class MockExecutionInterceptor implements HttlExecutionInterceptor {

		private HttlRequest lastRequest;

		private HttlResponse lastResponse;

		public HttlRequest getLastRequest() {
			return lastRequest;
		}

		public HttlResponse getLastResponse() {
			return lastResponse;
		}

		@Override
		public HttlResponse intercept(HttlRequest request, HttlExecutionChain chain) throws IOException {
			this.lastRequest = request;
			HttlResponse response = chain.next(request);
			this.lastResponse = response;
			return response;
		}

	}

	static class PageableSetter implements VarSetter<Pageable> {

		@Override
		public void set(Pageable value, String name, HttlRequestBuilder<?> request) {
			request.param(name + ".number", value.getPageNumber());
			request.param(name + ".size", value.getPageSize());
			request.param(name + ".sort", value.getSort());
		}

	}
}
