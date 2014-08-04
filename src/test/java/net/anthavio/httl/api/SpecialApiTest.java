package net.anthavio.httl.api;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Date;

import net.anthavio.httl.HttlBuilderInterceptor;
import net.anthavio.httl.HttlConstants;
import net.anthavio.httl.HttlExecutionChain;
import net.anthavio.httl.HttlExecutionInterceptor;
import net.anthavio.httl.HttlMarshaller;
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
		String json = Marshallers.marshall(sender.getConfig().getRequestMarshaller("application/json"), bean);
		// When
		MockBuilderInterceptor bldinc = new MockBuilderInterceptor();
		MockExecutionInterceptor exeinc = new MockExecutionInterceptor();
		String returned = api.intercept(bean, bldinc, exeinc);
		// Then
		//Assertions.assertThat(bldinc.getLastRequest()).isEqualTo(transport.getLastRequest());
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
		String bodyXml = Marshallers.marshall(sender.getConfig().getRequestMarshaller("application/xml"), bean);

		// When
		final Date dateToCheck = new Date();
		HttlResponseExtractor<Date> extractor = new HttlResponseExtractor<Date>() {

			@Override
			public Date extract(HttlResponse response) throws IOException {
				dateToCheck.setTime(bean.getDate().getTime());
				return bean.getDate();
			}

			@Override
			public HttlResponseExtractor<Date> supports(HttlResponse response) {
				return this;
			}

		};

		Date returnedDate = api.extractor(extractor, bean);
		// Then
		Assertions.assertThat(returnedDate).isEqualTo(bean.getDate());

		// When
		try {
			api.extractorWrong(bean, extractor);
			// Then
			Assertions.fail("IllegalArgumentException expected");
		} catch (IllegalArgumentException iax) {
			Assertions.assertThat(iax.getMessage()).startsWith("Incompatible ResponseExtractor");
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
		HttlMarshaller marshaller = new HttlMarshaller() {

			@Override
			public void write(Object requestBody, OutputStream stream, Charset charset) throws IOException {
				stream.write(((SomeBodyBean) requestBody).getName().getBytes("utf-8"));
			}

		};
		String returned = api.marshaller(marshaller, bean);

		// Then
		Assertions.assertThat(returned).isEqualTo(bean.getName());

	}

	static interface SpecialApi {

		@RestCall("POST /intercept")
		String intercept(@RestBody("application/json") SomeBodyBean bean, HttlBuilderInterceptor builderInterceptor,
				HttlExecutionInterceptor executionInterceptor);

		@RestCall("POST /extractor")
		Date extractor(HttlResponseExtractor<Date> extractor, @RestBody("application/xml") SomeBodyBean bean);

		@RestCall("POST /extractorSilly")
		String extractorWrong(@RestBody("application/xml") SomeBodyBean bean, HttlResponseExtractor<Date> extractor);

		@RestCall("POST /marshaller")
		@RestHeaders("Content-Type: application/xml")
		String marshaller(HttlMarshaller marshaller, @RestBody Object body);

		@RestCall("GET /customSetter")
		HttlResponse customSetter(@RestVar(value = "xpage", setter = PageableSetter.class) Pageable pager);

		@RestCall("POST /everything")
		SomeBodyBean everything(@RestVar(value = "page", setter = PageableSetter.class) Pageable pager,
				HttlMarshaller marshaller, @RestBody("application/json") Object body, HttlResponseExtractor extractor,
				HttlBuilderInterceptor builderInterceptor, HttlExecutionInterceptor executionInterceptor);
	}

	static class MockBuilderInterceptor implements HttlBuilderInterceptor {

		private HttlRequestBuilder builder;

		public HttlRequestBuilder getLastBuilder() {
			return builder;
		}

		@Override
		public void onBuild(HttlRequestBuilder<?> builder) {
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
