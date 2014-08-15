package net.anthavio.httl.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;

import net.anthavio.httl.HttlBuilderVisitor;
import net.anthavio.httl.HttlConstants;
import net.anthavio.httl.HttlExecutionChain;
import net.anthavio.httl.HttlExecutionFilter;
import net.anthavio.httl.HttlRequest;
import net.anthavio.httl.HttlRequestBuilders.HttlRequestBuilder;
import net.anthavio.httl.HttlResponse;
import net.anthavio.httl.HttlResponseExtractor;
import net.anthavio.httl.HttlSender;
import net.anthavio.httl.api.AdvancedApiTest.TestBodyBean;
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
		TestBuildVisitorAndExecFilter api = HttlApiBuilder.with(sender).build(TestBuildVisitorAndExecFilter.class);

		TestBodyBean bean = new TestBodyBean("Kvído Vymětal", new Date(), 999);
		// When
		MockBuilderVisitor bldinc = new MockBuilderVisitor();
		MockExecutionFilter exeinc = new MockExecutionFilter();
		String returned = api.intercept(bean, bldinc, exeinc);

		// Then - MockBuilderVisitor adds parameter
		Assertions.assertThat(exeinc.getLastRequest().getParameters().getFirst("dynamic")).isEqualTo("value");
		// Then - filter was executed
		Assertions.assertThat(exeinc.getLastRequest()).isEqualTo(transport.getLastRequest());
		Assertions.assertThat(exeinc.getLastResponse()).isEqualTo(transport.getLastResponse());
		Assertions.assertThat(transport.getLastRequest().getFirstHeader(HttlConstants.Content_Type)).startsWith(
				"application/json");
		Assertions.assertThat(transport.getLastResponse().getFirstHeader(HttlConstants.Content_Type)).startsWith(
				"application/json");

		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		sender.getConfig().getMarshaller().marshall(exeinc.getLastRequest(), baos);
		Assertions.assertThat(returned).isEqualTo(new String(baos.toByteArray(), "utf-8"));
	}

	static interface TestBuildVisitorAndExecFilter {

		@RestCall("POST /intercept")
		String intercept(@RestBody("application/json") TestBodyBean bean, HttlBuilderVisitor builderVisitor,
				HttlExecutionFilter executionFilter);
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
		final TestBodyBean bean = new TestBodyBean("Kvído Vymětal", new Date(), 999);
		//String bodyXml = Marshallers.marshall(sender.getConfig().getRequestMarshaller("application/xml"), bean);

		HttlResponseExtractor<Date> extractor = new TestDateExtractor(bean.getDate());
		Date returnedDate = api.extractor(extractor, bean);
		// Then
		Assertions.assertThat(returnedDate).isEqualTo(bean.getDate());
	}

	@Test
	public void wrongTypeExctractor() {
		// Given
		HttlSender sender = new MockSenderConfig().build();
		SpecialApi api = HttlApiBuilder.with(sender).build(SpecialApi.class);
		final TestBodyBean bean = new TestBodyBean("Kvído Vymětal", new Date(), 999);

		// Given - Extractor is anonymous class -> we cannot get actual generic parameter type via reflection
		HttlResponseExtractor<Date> extractor = new HttlResponseExtractor<Date>() {

			@Override
			public Date extract(HttlResponse response) throws IOException {
				return bean.getDate();
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
		String wrongExtractorType(@RestBody("application/xml") TestBodyBean bean, TestDateExtractor extractor);
	}

	static class TestDateExtractor implements HttlResponseExtractor<Date> {

		private Date date;

		public TestDateExtractor(Date date) {
			this.date = date;
		}

		@Override
		public Date extract(HttlResponse response) throws IOException {
			return date;
		}

	}

	@Test
	public void requestBodyWriterAsParameter() throws IOException {
		// Given
		HttlSender sender = new MockSenderConfig().build();
		ForBodyWriterTest api = HttlApiBuilder.with(sender).build(ForBodyWriterTest.class);
		TestBodyBean bean = new TestBodyBean("Kvído Vymětal", new Date(), 999);

		// When
		HttlBodyWriter<TestBodyBean> writer = new HttlBodyWriter<TestBodyBean>() {

			@Override
			public void write(TestBodyBean requestBody, OutputStream stream) throws IOException {
				stream.write(((TestBodyBean) requestBody).getName().getBytes("utf-8"));
			}

		};
		String returned = api.writerAsParam(writer, bean);

		// Then
		Assertions.assertThat(returned).isEqualTo(bean.getName());
	}

	@Test
	public void requestBodyWriterAsAttribute() throws IOException {
		// Given
		HttlSender sender = new MockSenderConfig().build();
		ForBodyWriterTest api = HttlApiBuilder.with(sender).build(ForBodyWriterTest.class);
		TestBodyBean bean = new TestBodyBean("Kvído Vymětal", new Date(), 999);

		//When 
		String returned = api.writerAsAttribute(bean);
		//Then 
		Assertions.assertThat(returned).isEqualTo(bean.getName());
	}

	static interface ForBodyWriterTest {

		@RestCall("POST /bodywriter1")
		@RestHeaders("Content-Type: application/xml")
		String writerAsParam(HttlBodyWriter<TestBodyBean> writer, @RestBody Object body);

		@RestCall("POST /bodywriter2")
		@RestHeaders("Content-Type: application/xml")
		String writerAsAttribute(@RestBody(writer = TestBodyWriter.class) TestBodyBean body);
	}

	static class TestBodyWriter implements HttlBodyWriter<TestBodyBean> {

		@Override
		public void write(TestBodyBean payload, OutputStream stream) throws IOException {
			stream.write(payload.getName().getBytes("utf-8"));
		}

	}

	static interface SpecialApi {

		@RestCall("POST /extractor")
		Date extractor(HttlResponseExtractor<Date> extractor, @RestBody("application/xml") TestBodyBean bean);

		@RestCall("POST /extractorSilly")
		String wrongExtractorType(@RestBody("application/xml") TestBodyBean bean, HttlResponseExtractor<Date> extractor);

		@RestCall("GET /customSetter")
		HttlResponse customSetter(@RestVar(name = "xpage", setter = PageableSetter.class) Pageable pager);

		@RestCall("POST /everything")
		TestBodyBean everything(@RestVar(name = "page", setter = PageableSetter.class) Pageable pager,
				HttlBodyWriter<Object> marshaller, @RestBody("application/json") Object body, HttlResponseExtractor extractor,
				HttlBuilderVisitor builderInterceptor, HttlExecutionFilter executionInterceptor);
	}

	static class MockBuilderVisitor implements HttlBuilderVisitor {

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

	static class MockExecutionFilter implements HttlExecutionFilter {

		private HttlRequest lastRequest;

		private HttlResponse lastResponse;

		public HttlRequest getLastRequest() {
			return lastRequest;
		}

		public HttlResponse getLastResponse() {
			return lastResponse;
		}

		@Override
		public HttlResponse filter(HttlRequest request, HttlExecutionChain chain) throws IOException {
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
